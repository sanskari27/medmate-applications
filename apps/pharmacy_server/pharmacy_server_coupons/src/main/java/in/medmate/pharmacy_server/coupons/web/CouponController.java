package in.medmate.pharmacy_server.coupons.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.coupons.CouponRules;
import in.medmate.pharmacy_server.identity.web.Current;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import in.medmate.pharmacy_server.shared.web.PageQuery;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile({"local", "prod", "identity-it"})
public class CouponController {

    private final CouponService coupons;

    public CouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping("/api/v1/coupons")
    public CouponViews.ListResponse list(
            Authentication authentication,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return coupons.list(Current.require(authentication), PageQuery.parse(page, size));
    }

    @GetMapping("/api/v1/coupons/{id}")
    public CouponViews.One read(Authentication authentication, @PathVariable UUID id) {
        return coupons.read(Current.require(authentication), id);
    }

    @PostMapping(path = "/api/v1/coupons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CouponViews.One> create(
            Authentication authentication,
            @RequestPart("coupon") JsonNode coupon,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coupons.create(Current.require(authentication), coupon, read(banner)));
    }

    @PutMapping(path = "/api/v1/coupons/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CouponViews.One update(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestPart("coupon") JsonNode coupon,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        return coupons.update(Current.require(authentication), id, coupon, read(banner));
    }

    @GetMapping("/api/v1/customer/pharmacies/{pharmacyId}/coupons")
    public CouponViews.StoreOffers pharmacy(Authentication authentication, @PathVariable String pharmacyId) {
        return coupons.forPharmacy(Current.require(authentication), pharmacyId);
    }

    @PostMapping("/api/v1/customer/coupons/preview")
    public CouponViews.Preview preview(Authentication authentication, @RequestBody JsonNode body) {
        return coupons.preview(Current.require(authentication), body);
    }

    private static CouponRules.Upload read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return new CouponRules.Upload(file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", "Banner must be an image");
        }
    }
}
