package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogOfferRulesTest {

    private static final UUID NEAR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FAR = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void theNearestListingWins() {
        CatalogOfferRules.Offer offer = CatalogOfferRules.nearest(
                        List.of(
                                new CatalogOfferRules.Pharmacy(FAR, "Far", 8),
                                new CatalogOfferRules.Pharmacy(NEAR, "Near", 1.2)),
                        List.of(
                                listing(FAR, "90.00", "80.00"),
                                listing(NEAR, "100.00", "95.00"),
                                new CatalogOfferRules.Listing(
                                        NEAR, "IN1", new BigDecimal("1.00"), new BigDecimal("1.00"), 0)))
                .get("IN1");

        assertThat(offer.pharmacyId()).isEqualTo(NEAR);
        assertThat(offer.pharmacyName()).isEqualTo("Near");
        assertThat(offer.onlinePrice()).isEqualByComparingTo("95.00");
    }

    private static CatalogOfferRules.Listing listing(UUID pharmacyId, String mrp, String price) {
        return new CatalogOfferRules.Listing(pharmacyId, "IN1", new BigDecimal(mrp), new BigDecimal(price), 4);
    }
}
