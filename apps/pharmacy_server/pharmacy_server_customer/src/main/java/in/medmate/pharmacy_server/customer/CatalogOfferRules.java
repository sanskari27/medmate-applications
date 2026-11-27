package in.medmate.pharmacy_server.customer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogOfferRules {

    private CatalogOfferRules() {}

    public record Pharmacy(UUID id, String name, double distanceKm) {}

    public record Listing(
            UUID pharmacyId, String productId, BigDecimal onlineMrp, BigDecimal onlinePrice, int availableQty) {}

    public record Offer(
            UUID pharmacyId, String pharmacyName, double distanceKm, BigDecimal onlineMrp, BigDecimal onlinePrice) {}

    public static Map<String, Offer> nearest(List<Pharmacy> pharmacies, List<Listing> listings) {
        Map<UUID, Pharmacy> byId = new LinkedHashMap<>();
        for (Pharmacy pharmacy : pharmacies) {
            byId.putIfAbsent(pharmacy.id(), pharmacy);
        }
        Map<String, Offer> chosen = new LinkedHashMap<>();
        for (Listing listing : listings) {
            if (listing.availableQty() <= 0) {
                continue;
            }
            Pharmacy pharmacy = byId.get(listing.pharmacyId());
            if (pharmacy == null) {
                continue;
            }
            Offer offer = new Offer(
                    pharmacy.id(), pharmacy.name(), pharmacy.distanceKm(), listing.onlineMrp(), listing.onlinePrice());
            Offer current = chosen.get(listing.productId());
            if (current == null || closer(offer, current)) {
                chosen.put(listing.productId(), offer);
            }
        }
        return chosen;
    }

    private static boolean closer(Offer candidate, Offer current) {
        int distance = Double.compare(candidate.distanceKm(), current.distanceKm());
        if (distance != 0) {
            return distance < 0;
        }
        return candidate.pharmacyId().compareTo(current.pharmacyId()) < 0;
    }
}
