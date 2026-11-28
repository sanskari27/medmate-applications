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
            String marketerName,
            String pack,
            String productForm,
            String composition,
            UUID pharmacyId,
            String pharmacyName,
            Double distanceKm,
            String onlineMrp,
            String onlinePrice,
            DeliveryCharge deliveryCharge) {}

    public record Page(List<Item> items, int page, int size, int total) {}

    public record SafetyNote(String topic, String level, String detail) {}

    public record Question(String question, String answer) {}

    public record Offer(
            UUID pharmacyId,
            String pharmacyName,
            double distanceKm,
            String onlineMrp,
            String onlinePrice,
            DeliveryCharge deliveryCharge,
            String licenceNumber,
            String pharmacistName,
            String city,
            String closesOn,
            boolean closed) {}

    public record Detail(
            String productId,
            String name,
            String displayName,
            String marketerName,
            String pack,
            String productForm,
            String composition,
            boolean rxRequired,
            List<String> categories,
            List<String> images,
            String introduction,
            String benefits,
            String howToUse,
            String ifMiss,
            String storage,
            String mrp,
            List<String> uses,
            List<String> sideEffects,
            List<SafetyNote> safety,
            List<Question> questions,
            List<Offer> offers,
            List<Item> similar) {}
}
