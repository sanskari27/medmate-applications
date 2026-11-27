package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.identity.pharmacy.PharmacyRules;
import java.util.List;
import java.util.UUID;

public final class NearbyViews {

    private NearbyViews() {}

    public record PharmacyView(
            UUID pharmacyId,
            String name,
            String address,
            String city,
            String state,
            String pincode,
            double latitude,
            double longitude,
            double distanceKm,
            boolean acceptOnlineOrders,
            String licenceNumber,
            String pharmacistName,
            List<PharmacyRules.OpeningHour> openingHours,
            boolean closed,
            String closesOn,
            DeliveryCharge deliveryCharge,
            Image image,
            boolean openAllHours) {}

    public record DeliveryCharge(String amount, double distanceKm) {}

    public record Image(String url) {}

    public record ListResponse(List<PharmacyView> pharmacies) {}
}
