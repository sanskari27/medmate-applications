package in.medmate.pharmacy_server.customer.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CustomerCatalogViews {

    private CustomerCatalogViews() {}

    public record DeliveryCharge(String amount, double distanceKm) {}

    public record Item(
            String productId,
            String name,
            String displayName,
            BigDecimal price,
            String image,
            boolean rxRequired,
            UUID pharmacyId,
            String pharmacyName,
            Double distanceKm,
            String onlineMrp,
            String onlinePrice,
            DeliveryCharge deliveryCharge) {}

    public record Page(List<Item> items, int page, int size, int total) {}
}
