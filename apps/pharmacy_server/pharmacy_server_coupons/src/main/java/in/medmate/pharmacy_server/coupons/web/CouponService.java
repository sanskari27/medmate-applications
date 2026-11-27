package in.medmate.pharmacy_server.coupons.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.coupons.CouponAccess;
import in.medmate.pharmacy_server.coupons.CouponRules;
import in.medmate.pharmacy_server.coupons.store.CouponStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import in.medmate.pharmacy_server.identity.documents.DocumentStore;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import in.medmate.pharmacy_server.shared.web.PageQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
public class CouponService {

    private final CouponStore coupons;
    private final DocumentStore documents;
    private final MedmateIdentityProperties properties;
    private final Clock clock;

    public CouponService(
            CouponStore coupons, DocumentStore documents, MedmateIdentityProperties properties, Clock clock) {
        this.coupons = coupons;
        this.documents = documents;
        this.properties = properties;
        this.clock = clock;
    }

    public CouponViews.ListResponse list(AccountPrincipal actor, PageQuery page) {
        requireManage(actor);
        List<CouponViews.Summary> rows = coupons.page(page.size(), page.offset()).stream()
                .map(this::summary)
                .toList();
        return new CouponViews.ListResponse(rows, page.page(), page.size(), coupons.count());
    }

    public CouponViews.One read(AccountPrincipal actor, UUID id) {
        requireManage(actor);
        return coupons.find(id).map(this::one).orElseThrow(CouponService::missing);
    }

    @Transactional
    public CouponViews.One create(AccountPrincipal actor, JsonNode body, CouponRules.Upload banner) {
        requireManage(actor);
        CouponRules.Prepared prepared = CouponRules.prepareCreate(body);
        if (prepared.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, prepared.problem());
        }
        String bannerProblem = CouponRules.bannerProblem(banner, true);
        if (bannerProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, bannerProblem);
        }
        if (!coupons.pharmaciesExist(prepared.draft().pharmacyIds())) {
            throw problem(HttpStatus.BAD_REQUEST, "Pharmacy was not found");
        }
        UUID id = UUID.randomUUID();
        String contentType = CouponRules.media(banner.contentType());
        String key = bannerKey(id);
        documents.put(key, contentType, banner.body());
        try {
            return one(coupons.insert(id, prepared.draft(), key, contentType, clock.instant()));
        } catch (DuplicateKeyException exception) {
            documents.delete(key);
            throw problem(HttpStatus.BAD_REQUEST, "Coupon code is already in use");
        }
    }

    @Transactional
    public CouponViews.One update(AccountPrincipal actor, UUID id, JsonNode body, CouponRules.Upload banner) {
        requireManage(actor);
        CouponStore.CouponRow current = coupons.find(id).orElseThrow(CouponService::missing);
        CouponRules.Prepared prepared = CouponRules.prepareUpdate(body, draft(current));
        if (prepared.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, prepared.problem());
        }
        String bannerProblem = CouponRules.bannerProblem(banner, false);
        if (bannerProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, bannerProblem);
        }
        if (!coupons.pharmaciesExist(prepared.draft().pharmacyIds())) {
            throw problem(HttpStatus.BAD_REQUEST, "Pharmacy was not found");
        }
        String key = current.bannerKey();
        String contentType = current.bannerContentType();
        if (banner != null && banner.body() != null && banner.body().length > 0) {
            contentType = CouponRules.media(banner.contentType());
            key = bannerKey(id);
            documents.delete(current.bannerKey());
            documents.put(key, contentType, banner.body());
        }
        return coupons.update(id, prepared.draft(), key, contentType, clock.instant())
                .map(this::one)
                .orElseThrow(CouponService::missing);
    }

    public CouponViews.Preview preview(AccountPrincipal actor, JsonNode body) {
        if (!CouponAccess.customer(actor)) {
            throw problem(HttpStatus.FORBIDDEN, "This action is not allowed.");
        }
        CouponRules.PreviewRequest request = CouponRules.previewRequest(body);
        if (request.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, request.problem());
        }
        CouponStore.CouponRow row = coupons.findByCode(request.code())
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "Coupon is not recognised"));
        CouponRules.Quote quote = CouponRules.quote(
                snapshot(row),
                row.redemptionCount(),
                coupons.redemptionsForCustomer(row.id(), actor.id()),
                request.orderTotal(),
                request.pharmacyId(),
                clock.instant());
        if (quote.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, quote.problem());
        }
        return new CouponViews.Preview(
                row.code(),
                row.title(),
                row.discountType(),
                money(row.discountValue()),
                money(quote.orderTotal()),
                money(quote.discount()),
                money(quote.payable()),
                banner(row));
    }

    private CouponViews.One one(CouponStore.CouponRow row) {
        List<UUID> pharmacyIds = "selected".equals(row.pharmacyScope()) ? coupons.pharmacyIds(row.id()) : List.of();
        return new CouponViews.One(detail(row, pharmacyIds));
    }

    private CouponViews.Summary summary(CouponStore.CouponRow row) {
        return new CouponViews.Summary(
                row.id(),
                row.code(),
                row.title(),
                row.discountType(),
                money(row.discountValue()),
                money(row.maxDiscount()),
                money(row.minOrder()),
                row.startsAt(),
                row.endsAt(),
                row.maxRedemptions(),
                row.maxPerCustomer(),
                row.pharmacyScope(),
                row.pharmacyCount(),
                row.active(),
                row.redemptionCount(),
                banner(row));
    }

    private CouponViews.Detail detail(CouponStore.CouponRow row, List<UUID> pharmacyIds) {
        return new CouponViews.Detail(
                row.id(),
                row.code(),
                row.title(),
                row.discountType(),
                money(row.discountValue()),
                money(row.maxDiscount()),
                money(row.minOrder()),
                row.startsAt(),
                row.endsAt(),
                row.maxRedemptions(),
                row.maxPerCustomer(),
                row.pharmacyScope(),
                pharmacyIds,
                row.active(),
                row.redemptionCount(),
                banner(row),
                row.createdAt(),
                row.updatedAt());
    }

    private CouponViews.Banner banner(CouponStore.CouponRow row) {
        return new CouponViews.Banner(row.bannerContentType(), documents.url(row.bannerKey()));
    }

    private CouponRules.Draft draft(CouponStore.CouponRow row) {
        List<UUID> pharmacyIds = "selected".equals(row.pharmacyScope()) ? coupons.pharmacyIds(row.id()) : List.of();
        return new CouponRules.Draft(
                row.code(),
                row.title(),
                row.discountType(),
                row.discountValue(),
                row.maxDiscount(),
                row.minOrder(),
                row.startsAt(),
                row.endsAt(),
                row.maxRedemptions(),
                row.maxPerCustomer(),
                row.pharmacyScope(),
                pharmacyIds,
                row.active());
    }

    private CouponRules.Snapshot snapshot(CouponStore.CouponRow row) {
        List<UUID> pharmacyIds = "selected".equals(row.pharmacyScope()) ? coupons.pharmacyIds(row.id()) : List.of();
        return new CouponRules.Snapshot(
                row.code(),
                row.title(),
                row.discountType(),
                row.discountValue(),
                row.maxDiscount(),
                row.minOrder(),
                row.startsAt(),
                row.endsAt(),
                row.maxRedemptions(),
                row.maxPerCustomer(),
                row.pharmacyScope(),
                pharmacyIds,
                row.active());
    }

    private String bannerKey(UUID id) {
        return properties.uploadPrefix() + "/coupon-banners/" + id;
    }

    private static void requireManage(AccountPrincipal actor) {
        if (!CouponAccess.manage(actor)) {
            throw problem(HttpStatus.FORBIDDEN, "This action is not allowed.");
        }
    }

    private static ApiProblem missing() {
        return problem(HttpStatus.NOT_FOUND, "Coupon was not found");
    }

    private static ApiProblem problem(HttpStatus status, String detail) {
        String title =
                switch (status) {
                    case BAD_REQUEST -> "Bad request";
                    case FORBIDDEN -> "Forbidden";
                    case NOT_FOUND -> "Not found";
                    default -> status.getReasonPhrase();
                };
        return new ApiProblem(status, title, detail);
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
