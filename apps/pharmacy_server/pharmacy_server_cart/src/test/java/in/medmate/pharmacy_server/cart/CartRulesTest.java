package in.medmate.pharmacy_server.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.medmate.pharmacy_server.coupons.CouponRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartRulesTest {

    private static final UUID NEAR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FAR = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID ONLY = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-11-28T00:00:00Z");

    @Test
    void onePharmacyThatCoversTheCartStillListsTheOther() {
        CartRules.Arrangement arrangement = CartRules.arrange(
                List.of(line("A", 1), line("B", 2)),
                List.of(
                        offer(NEAR, "Near", 1.0, "A", 4),
                        offer(NEAR, "Near", 1.0, "B", 4),
                        offer(FAR, "Far", 6.0, "A", 4),
                        offer(FAR, "Far", 6.0, "B", 4)),
                List.of());

        assertThat(arrangement.selectionSource()).isEqualTo(CartRules.RECOMMENDED);
        assertThat(arrangement.lines()).allMatch(line -> NEAR.equals(line.assignedPharmacyId()) && line.available());
        assertThat(arrangement.lines().get(0).alternatives())
                .extracting(CartRules.Offer::pharmacyId)
                .containsExactly(NEAR, FAR);
        assertThat(pharmacy(arrangement, NEAR).recommended()).isTrue();
        assertThat(pharmacy(arrangement, NEAR).selected()).isTrue();
        assertThat(pharmacy(arrangement, FAR).recommended()).isFalse();
        assertThat(pharmacy(arrangement, FAR).productIds()).containsExactly("A", "B");
    }

    @Test
    void theLargerCoverWinsAndTheNearerPharmacyBreaksATie() {
        CartRules.Arrangement grouped = CartRules.arrange(
                List.of(line("A", 1), line("B", 1)),
                List.of(
                        offer(NEAR, "Near", 1.0, "A", 4),
                        offer(FAR, "Far", 9.0, "A", 4),
                        offer(FAR, "Far", 9.0, "B", 4)),
                List.of());
        assertThat(grouped.lines()).allMatch(line -> FAR.equals(line.assignedPharmacyId()));

        CartRules.Arrangement tie = CartRules.arrange(
                List.of(line("A", 1), line("B", 1)),
                List.of(
                        offer(FAR, "Far", 9.0, "A", 4),
                        offer(FAR, "Far", 9.0, "B", 4),
                        offer(NEAR, "Near", 1.0, "A", 4),
                        offer(NEAR, "Near", 1.0, "B", 4)),
                List.of());
        assertThat(tie.lines()).allMatch(line -> NEAR.equals(line.assignedPharmacyId()));
    }

    @Test
    void aChosenPharmacyLeavesLinesItCannotFillUnassigned() {
        CartRules.Arrangement arrangement = CartRules.arrange(
                List.of(line("A", 1), line("B", 1)),
                List.of(
                        offer(NEAR, "Near", 1.0, "A", 4),
                        offer(NEAR, "Near", 1.0, "B", 4),
                        offer(ONLY, "Only", 3.0, "A", 4)),
                List.of(ONLY));

        assertThat(arrangement.selectionSource()).isEqualTo(CartRules.CHOSEN);
        CartRules.LinePlan first = arrangement.lines().get(0);
        CartRules.LinePlan second = arrangement.lines().get(1);
        assertThat(first.assignedPharmacyId()).isEqualTo(ONLY);
        assertThat(first.available()).isTrue();
        assertThat(second.available()).isFalse();
        assertThat(second.assignedPharmacyId()).isNull();
        assertThat(second.alternatives())
                .extracting(CartRules.Offer::pharmacyId)
                .containsExactly(NEAR);
        assertThat(pharmacy(arrangement, ONLY).selected()).isTrue();
        assertThat(pharmacy(arrangement, NEAR).recommended()).isTrue();
        assertThat(pharmacy(arrangement, NEAR).selected()).isFalse();
    }

    @Test
    void aFlatCouponDiscountsEachEligiblePharmacyGroup() {
        CartRules.Arrangement arrangement = CartRules.arrange(
                List.of(line("A", 2), line("B", 1)),
                List.of(offer(NEAR, "Near", 1.0, "A", 4), offer(FAR, "Far", 4.0, "B", 4)),
                List.of());
        CouponRules.Snapshot coupon = new CouponRules.Snapshot(
                "SAVE50",
                "Save",
                "flat",
                new BigDecimal("50.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                "all",
                List.of(),
                true);
        CouponRules.Quote near =
                CouponRules.quote(coupon, 0, 0, pharmacy(arrangement, NEAR).merchandise(), NEAR, NOW);
        CouponRules.Quote far =
                CouponRules.quote(coupon, 0, 0, pharmacy(arrangement, FAR).merchandise(), FAR, NOW);

        assertThat(near.discount()).isEqualByComparingTo("50.00");
        assertThat(far.discount()).isEqualByComparingTo("50.00");
        assertThat(CartRules.payable(
                        pharmacy(arrangement, NEAR).merchandise(), near.discount(), new BigDecimal("0.00")))
                .isEqualByComparingTo("130.00");

        CouponRules.Snapshot selected = new CouponRules.Snapshot(
                "SHOP",
                "Shop",
                "flat",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                "selected",
                List.of(NEAR),
                true);
        assertThat(CouponRules.quote(selected, 0, 0, pharmacy(arrangement, FAR).merchandise(), FAR, NOW)
                        .problem())
                .isEqualTo("Coupon does not apply at this pharmacy");
    }

    @Test
    void lineAndPharmacyInputAreChecked() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode line = json.createObjectNode();
        line.put("productId", "IN1");
        line.put("qty", 2);
        assertThat(CartRules.line(line).problem()).isNull();
        line.put("qty", 0);
        assertThat(CartRules.line(line).problem()).isEqualTo("Quantity must be from 1 to 99");

        ObjectNode body = json.createObjectNode();
        ArrayNode ids = body.putArray("pharmacyIds");
        ids.add(NEAR.toString());
        assertThat(CartRules.pharmacies(body).pharmacyIds()).containsExactly(NEAR);
        ids.add("nope");
        assertThat(CartRules.pharmacies(body).problem()).isEqualTo("Pharmacy is not valid");
    }

    private static CartRules.Line line(String productId, int qty) {
        return new CartRules.Line(productId, qty);
    }

    private static CartRules.Offer offer(UUID pharmacyId, String name, double distanceKm, String productId, int qty) {
        return new CartRules.Offer(
                pharmacyId, name, distanceKm, productId, new BigDecimal("100.00"), new BigDecimal("90.00"), qty);
    }

    private static CartRules.PharmacyPlan pharmacy(CartRules.Arrangement arrangement, UUID id) {
        return arrangement.pharmacies().stream()
                .filter(plan -> plan.pharmacyId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
