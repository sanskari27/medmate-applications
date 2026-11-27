package in.medmate.pharmacy_server.cart.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.cart.CartRules;
import in.medmate.pharmacy_server.cart.store.CartStore;
import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.coupons.CouponRules;
import in.medmate.pharmacy_server.coupons.web.CouponService;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.DeliveryChargePolicy;
import in.medmate.pharmacy_server.customer.NearbyRules;
import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CartService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern COUPON = Pattern.compile("[A-Z0-9-]{4,20}");
    private static final String SELECT_ADDRESS = "Select a delivery address";

    private final CartStore carts;
    private final AddressStore addresses;
    private final NearbyStore nearby;
    private final StockStore stock;
    private final CatalogStore catalog;
    private final CouponService coupons;
    private final Clock clock;

    public CartService(
            CartStore carts,
            AddressStore addresses,
            NearbyStore nearby,
            StockStore stock,
            CatalogStore catalog,
            CouponService coupons,
            Clock clock) {
        this.carts = carts;
        this.addresses = addresses;
        this.nearby = nearby;
        this.stock = stock;
        this.catalog = catalog;
        this.coupons = coupons;
        this.clock = clock;
    }

    public CartViews.Cart get(AccountPrincipal actor) {
        requireCustomer(actor);
        return view(actor);
    }

    @Transactional
    public CartViews.Cart saveLine(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        CartRules.LineChange change = CartRules.line(body);
        if (change.problem() != null) {
            throw bad(change.problem());
        }
        if (!known(change.productId())) {
            throw bad("Product was not found");
        }
        carts.ensure(actor.id(), clock.instant());
        if (!carts.hasLine(actor.id(), change.productId()) && carts.lineCount(actor.id()) >= CartRules.MAX_LINES) {
            throw bad("Cart is full");
        }
        carts.saveLine(actor.id(), change.productId(), change.qty(), clock.instant());
        return view(actor);
    }

    @Transactional
    public CartViews.Cart deleteLine(AccountPrincipal actor, String productId) {
        requireCustomer(actor);
        carts.ensure(actor.id(), clock.instant());
        carts.deleteLine(actor.id(), productId == null ? "" : productId.trim(), clock.instant());
        return view(actor);
    }

    @Transactional
    public CartViews.Cart savePharmacies(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        CartRules.PharmacyChoice choice = CartRules.pharmacies(body);
        if (choice.problem() != null) {
            throw bad(choice.problem());
        }
        carts.ensure(actor.id(), clock.instant());
        carts.replacePharmacies(actor.id(), choice.pharmacyIds(), clock.instant());
        return view(actor);
    }

    @Transactional
    public CartViews.Cart saveCoupon(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        String code = couponCode(body);
        if (coupons.quoteFor(actor.id(), code, BigDecimal.ONE, null).isEmpty()) {
            throw bad("Coupon is not recognised");
        }
        carts.ensure(actor.id(), clock.instant());
        carts.coupon(actor.id(), code, clock.instant());
        return view(actor);
    }

    @Transactional
    public CartViews.Cart clearCoupon(AccountPrincipal actor) {
        requireCustomer(actor);
        carts.ensure(actor.id(), clock.instant());
        carts.clearCoupon(actor.id(), clock.instant());
        return view(actor);
    }

    private CartViews.Cart view(AccountPrincipal actor) {
        CartStore.CartRow cart = carts.find(actor.id()).orElse(null);
        List<CartStore.LineRow> stored = cart == null ? List.of() : carts.lines(actor.id());
        List<UUID> chosen = cart == null ? List.of() : carts.pharmacies(actor.id());
        String couponCode = cart == null ? null : cart.couponCode();
        AddressStore.AddressRow address = addresses.selected(actor.id()).orElse(null);
        List<CartRules.Line> lines = new ArrayList<>();
        for (CartStore.LineRow row : stored) {
            lines.add(new CartRules.Line(row.productId(), row.qty()));
        }
        CartRules.Arrangement arrangement =
                CartRules.arrange(lines, address == null ? List.of() : offers(address, lines), chosen);
        Map<String, String> names = names(lines);
        Priced priced = price(actor.id(), couponCode, arrangement);
        List<CartViews.Line> lineViews = new ArrayList<>();
        for (CartRules.LinePlan line : arrangement.lines()) {
            lineViews.add(lineView(line, names.getOrDefault(line.productId(), line.productId())));
        }
        return new CartViews.Cart(
                lineViews,
                priced.pharmacies(),
                couponCode == null ? null : new CartViews.CouponAttachment(couponCode, priced.couponProblem()),
                priced.totals(),
                arrangement.selectionSource(),
                address == null ? SELECT_ADDRESS : null);
    }

    private List<CartRules.Offer> offers(AddressStore.AddressRow address, List<CartRules.Line> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        List<NearbyStore.NearbyRow> pharmacies =
                nearby.within(address.latitude(), address.longitude(), NearbyRules.MAX_DISTANCE_KM);
        if (pharmacies.isEmpty()) {
            return List.of();
        }
        Map<UUID, NearbyStore.NearbyRow> byId = new LinkedHashMap<>();
        for (NearbyStore.NearbyRow pharmacy : pharmacies) {
            byId.put(pharmacy.pharmacyId(), pharmacy);
        }
        List<String> productIds = new ArrayList<>();
        for (CartRules.Line line : lines) {
            productIds.add(line.productId());
        }
        String month = YearMonth.now(clock.withZone(ZONE)).toString();
        List<CartRules.Offer> offers = new ArrayList<>();
        for (StockStore.OnlineListing listing : stock.onlineListings(byId.keySet(), productIds, month)) {
            NearbyStore.NearbyRow pharmacy = byId.get(listing.pharmacyId());
            if (pharmacy == null) {
                continue;
            }
            offers.add(new CartRules.Offer(
                    pharmacy.pharmacyId(),
                    pharmacy.name(),
                    pharmacy.distanceKm(),
                    listing.productId(),
                    listing.onlineMrp(),
                    listing.onlinePrice(),
                    listing.availableQty()));
        }
        return offers;
    }

    private Priced price(UUID customerId, String couponCode, CartRules.Arrangement arrangement) {
        boolean missing = couponCode != null
                && coupons.quoteFor(customerId, couponCode, BigDecimal.ONE, null)
                        .isEmpty();
        BigDecimal merchandise = BigDecimal.ZERO;
        BigDecimal delivery = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        String couponProblem = missing ? "Coupon is not recognised" : null;
        List<CartViews.Pharmacy> pharmacies = new ArrayList<>();
        for (CartRules.PharmacyPlan plan : arrangement.pharmacies()) {
            BigDecimal groupMerchandise = plan.merchandise();
            DeliveryChargePolicy.Quote charge = plan.distanceKm() == null
                    ? new DeliveryChargePolicy.Quote(BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY), 0)
                    : DeliveryChargePolicy.quote(plan.distanceKm());
            BigDecimal groupDelivery = plan.assignedProductIds().isEmpty()
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                    : charge.amount();
            BigDecimal groupDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
            String problem = null;
            if (!plan.assignedProductIds().isEmpty() && couponCode != null) {
                if (missing) {
                    problem = "Coupon is not recognised";
                } else {
                    Optional<CouponRules.Quote> quote =
                            coupons.quoteFor(customerId, couponCode, groupMerchandise, plan.pharmacyId());
                    if (quote.isEmpty()) {
                        problem = "Coupon is not recognised";
                    } else if (quote.get().problem() != null) {
                        problem = quote.get().problem();
                    } else {
                        groupDiscount = quote.get().discount();
                    }
                }
            }
            BigDecimal groupPayable = CartRules.payable(groupMerchandise, groupDiscount, groupDelivery);
            if (!plan.assignedProductIds().isEmpty()) {
                merchandise = merchandise.add(groupMerchandise);
                delivery = delivery.add(groupDelivery);
                discount = discount.add(groupDiscount);
                payable = payable.add(groupPayable);
            }
            CartViews.DeliveryCharge deliveryView = plan.distanceKm() == null
                    ? null
                    : new CartViews.DeliveryCharge(money(charge.amount()), charge.distanceKm());
            pharmacies.add(new CartViews.Pharmacy(
                    plan.pharmacyId(),
                    plan.name(),
                    plan.distanceKm(),
                    plan.recommended(),
                    plan.selected(),
                    plan.eligible(),
                    plan.productIds(),
                    plan.assignedProductIds(),
                    money(groupMerchandise),
                    deliveryView,
                    money(groupDiscount),
                    problem,
                    money(groupPayable)));
        }
        return new Priced(
                pharmacies,
                couponProblem,
                new CartViews.Totals(money(merchandise), money(delivery), money(discount), money(payable)));
    }

    private CartViews.Line lineView(CartRules.LinePlan line, String name) {
        List<CartViews.Alternative> alternatives = new ArrayList<>();
        for (CartRules.Offer offer : line.alternatives()) {
            DeliveryChargePolicy.Quote charge = DeliveryChargePolicy.quote(offer.distanceKm());
            alternatives.add(new CartViews.Alternative(
                    offer.pharmacyId(),
                    offer.pharmacyName(),
                    offer.distanceKm(),
                    money(offer.onlineMrp()),
                    money(offer.onlinePrice()),
                    offer.availableQty(),
                    new CartViews.DeliveryCharge(money(charge.amount()), charge.distanceKm())));
        }
        return new CartViews.Line(
                line.productId(),
                name,
                line.qty(),
                line.assignedPharmacyId(),
                money(line.onlineMrp()),
                money(line.onlinePrice()),
                money(line.lineTotal()),
                line.available(),
                alternatives);
    }

    private Map<String, String> names(List<CartRules.Line> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>();
        for (CartRules.Line line : lines) {
            ids.add(line.productId());
        }
        CatalogStore.Found found = catalog.findMany(ids);
        Map<String, String> names = new LinkedHashMap<>();
        for (CatalogDocuments.DrugDetail drug : found.drug()) {
            names.put(drug.productId(), label(drug.displayName(), drug.productName()));
        }
        for (CatalogDocuments.FmcgDetail fmcg : found.fmcg()) {
            names.put(fmcg.productId(), label(fmcg.displayName(), fmcg.productName()));
        }
        return names;
    }

    private boolean known(String productId) {
        CatalogStore.Found found = catalog.findMany(List.of(productId));
        return found.missing().isEmpty();
    }

    private static String couponCode(JsonNode body) {
        if (body == null
                || !body.isObject()
                || body.get("code") == null
                || !body.get("code").isTextual()) {
            throw bad("Coupon code is required");
        }
        String code = body.get("code").asText().trim().toUpperCase();
        if (!COUPON.matcher(code).matches()) {
            throw bad("Coupon code is not valid");
        }
        return code;
    }

    private static String label(String displayName, String productName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return productName;
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void requireCustomer(AccountPrincipal actor) {
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
    }

    private static ApiProblem bad(String detail) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private record Priced(List<CartViews.Pharmacy> pharmacies, String couponProblem, CartViews.Totals totals) {}
}
