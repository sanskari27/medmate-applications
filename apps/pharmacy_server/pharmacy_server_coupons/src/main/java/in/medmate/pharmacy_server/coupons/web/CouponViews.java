package in.medmate.pharmacy_server.coupons.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CouponViews {

    private CouponViews() {}

    public record Banner(String contentType, String url) {}

    public record Summary(
            UUID id,
            String code,
            String title,
            String discountType,
            String discountValue,
            String maxDiscount,
            String minOrder,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String pharmacyScope,
            int pharmacyCount,
            boolean active,
            int redemptions,
            Banner banner) {}

    public record Detail(
            UUID id,
            String code,
            String title,
            String discountType,
            String discountValue,
            String maxDiscount,
            String minOrder,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String pharmacyScope,
            List<UUID> pharmacyIds,
            boolean active,
            int redemptions,
            Banner banner,
            Instant createdAt,
            Instant updatedAt) {}

    public record ListResponse(List<Summary> coupons, int page, int size, int total) {}

    public record One(Detail coupon) {}

    public record StoreOffer(UUID id, String code, String title, String discountType, String discountValue) {}

    public record StoreOffers(List<StoreOffer> coupons) {}

    public record Preview(
            String code,
            String title,
            String discountType,
            String discountValue,
            String orderTotal,
            String discount,
            String payable,
            Banner banner) {}
}
