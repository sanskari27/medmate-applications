package in.medmate.pharmacy_server.cart.web;

import java.util.List;
import java.util.UUID;

public final class CartViews {

    private CartViews() {}

    public record DeliveryCharge(String amount, double distanceKm) {}

    public record Alternative(
            UUID pharmacyId,
            String pharmacyName,
            double distanceKm,
            String onlineMrp,
            String onlinePrice,
            int qtyAvailable,
            DeliveryCharge deliveryCharge) {}

    public record Line(
            String productId,
            String name,
            int qty,
            UUID assignedPharmacyId,
            String onlineMrp,
            String onlinePrice,
            String lineTotal,
            boolean available,
            List<Alternative> alternatives) {}

    public record Coupon(String code, String discount, String problem) {}

    public record Pharmacy(
            UUID pharmacyId,
            String name,
            Double distanceKm,
            boolean recommended,
            boolean selected,
            boolean eligible,
            List<String> productIds,
            List<String> assignedProductIds,
            String merchandise,
            DeliveryCharge deliveryCharge,
            String discount,
            String problem,
            String payable) {}

    public record CouponAttachment(String code, String problem) {}

    public record Totals(String merchandise, String delivery, String discount, String payable) {}

    public record Cart(
            List<Line> lines,
            List<Pharmacy> pharmacies,
            CouponAttachment coupon,
            Totals totals,
            String selectionSource,
            String notice) {}
}
