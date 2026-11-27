package in.medmate.pharmacy_server.customer.web;

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
            boolean acceptOnlineOrders) {}

    public record ListResponse(List<PharmacyView> pharmacies) {}
}
