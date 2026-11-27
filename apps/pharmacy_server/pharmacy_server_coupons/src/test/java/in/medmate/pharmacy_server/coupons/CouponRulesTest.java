package in.medmate.pharmacy_server.coupons;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CouponRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID PHARMACY = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aSimplePercentCouponStoresAnUppercaseCode() throws Exception {
        CouponRules.Prepared prepared = CouponRules.prepareCreate(body());

        assertThat(prepared.problem()).isNull();
        assertThat(prepared.draft().code()).isEqualTo("WELCOME10");
        assertThat(prepared.draft().title()).isEqualTo("Welcome offer");
        assertThat(prepared.draft().discountType()).isEqualTo("pct");
        assertThat(prepared.draft().discountValue()).isEqualByComparingTo("10.00");
        assertThat(prepared.draft().pharmacyScope()).isEqualTo("all");
        assertThat(prepared.draft().pharmacyIds()).isEmpty();
        assertThat(prepared.draft().active()).isTrue();
        assertThat(prepared.draft().maxDiscount()).isNull();
        assertThat(prepared.draft().minOrder()).isNull();
    }

    @Test
    void optionalLimitsAreKeptWhenTheyAreSet() throws Exception {
        ObjectNode body = body();
        body.put("discountType", "pct");
        body.put("discountValue", "15");
        body.put("maxDiscount", "40.00");
        body.put("minOrder", "200");
        body.put("startsAt", "2026-11-01T00:00:00Z");
        body.put("endsAt", "2026-12-01T00:00:00Z");
        body.put("maxRedemptions", 100);
        body.put("maxPerCustomer", 1);
        body.put("pharmacyScope", "selected");
        body.putArray("pharmacyIds").add(PHARMACY.toString());
        body.put("active", false);

        CouponRules.Prepared prepared = CouponRules.prepareCreate(body);

        assertThat(prepared.problem()).isNull();
        assertThat(prepared.draft().maxDiscount()).isEqualByComparingTo("40.00");
        assertThat(prepared.draft().minOrder()).isEqualByComparingTo("200.00");
        assertThat(prepared.draft().startsAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(prepared.draft().endsAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(prepared.draft().maxRedemptions()).isEqualTo(100);
        assertThat(prepared.draft().maxPerCustomer()).isEqualTo(1);
        assertThat(prepared.draft().pharmacyIds()).containsExactly(PHARMACY);
        assertThat(prepared.draft().active()).isFalse();
    }

    @Test
    void invalidCouponFieldsAreRefused() throws Exception {
        ObjectNode missingTitle = body();
        missingTitle.remove("title");
        assertThat(CouponRules.prepareCreate(missingTitle).problem()).isEqualTo("Title is required");

        ObjectNode shortCode = body();
        shortCode.put("code", "ab");
        assertThat(CouponRules.prepareCreate(shortCode).problem()).isEqualTo("Coupon code is not valid");

        ObjectNode zero = body();
        zero.put("discountValue", 0);
        assertThat(CouponRules.prepareCreate(zero).problem()).isEqualTo("Discount must be greater than zero");

        ObjectNode overPercent = body();
        overPercent.put("discountValue", "100.01");
        assertThat(CouponRules.prepareCreate(overPercent).problem()).isEqualTo("Percent discount cannot exceed 100");

        ObjectNode flatCap = body();
        flatCap.put("discountType", "flat");
        flatCap.put("discountValue", "25");
        flatCap.put("maxDiscount", "10");
        assertThat(CouponRules.prepareCreate(flatCap).problem())
                .isEqualTo("Maximum discount is only for a percent coupon");

        ObjectNode window = body();
        window.put("startsAt", "2026-12-01T00:00:00Z");
        window.put("endsAt", "2026-12-01T00:00:00Z");
        assertThat(CouponRules.prepareCreate(window).problem()).isEqualTo("End must be after the start");

        ObjectNode noPharmacy = body();
        noPharmacy.put("pharmacyScope", "selected");
        assertThat(CouponRules.prepareCreate(noPharmacy).problem()).isEqualTo("Choose at least one pharmacy");
    }

    @Test
    void theCodeCannotChange() throws Exception {
        CouponRules.Draft current = CouponRules.prepareCreate(body()).draft();
        ObjectNode renamed = body();
        renamed.put("code", "OTHER");

        assertThat(CouponRules.prepareUpdate(renamed, current).problem()).isEqualTo("Coupon code cannot be changed");

        ObjectNode same = body();
        same.put("code", "welcome10");
        assertThat(CouponRules.prepareUpdate(same, current).problem()).isNull();
        assertThat(CouponRules.prepareUpdate(same, current).draft().code()).isEqualTo("WELCOME10");
    }

    @Test
    void bannerMustBeASmallImage() {
        assertThat(CouponRules.bannerProblem(null, true)).isEqualTo("Banner is required");
        assertThat(CouponRules.bannerProblem(null, false)).isNull();
        assertThat(CouponRules.bannerProblem(new CouponRules.Upload("text/plain", new byte[] {1}), true))
                .isEqualTo("Banner must be an image");
        assertThat(CouponRules.bannerProblem(new CouponRules.Upload("image/jpg", new byte[] {1}), true))
                .isNull();
        assertThat(CouponRules.media("image/jpg")).isEqualTo("image/jpeg");
        byte[] huge = new byte[CouponRules.BANNER_MAX + 1];
        assertThat(CouponRules.bannerProblem(new CouponRules.Upload("image/png", huge), true))
                .isEqualTo("Banner is too large");
    }

    @Test
    void percentDiscountRespectsTheCeilingAndTheOrderTotal() {
        CouponRules.Quote quote =
                CouponRules.quote(snapshot("pct", "10.00", null, null), 0, 0, new BigDecimal("500"), null, NOW);
        assertThat(quote.problem()).isNull();
        assertThat(quote.discount()).isEqualByComparingTo("50.00");
        assertThat(quote.payable()).isEqualByComparingTo("450.00");

        CouponRules.Quote capped =
                CouponRules.quote(snapshot("pct", "10.00", "30.00", null), 0, 0, new BigDecimal("500"), null, NOW);
        assertThat(capped.discount()).isEqualByComparingTo("30.00");
        assertThat(capped.payable()).isEqualByComparingTo("470.00");

        CouponRules.Quote rounded =
                CouponRules.quote(snapshot("pct", "10.00", null, null), 0, 0, new BigDecimal("10.05"), null, NOW);
        assertThat(rounded.discount()).isEqualByComparingTo("1.01");
        assertThat(rounded.payable()).isEqualByComparingTo("9.04");
    }

    @Test
    void flatDiscountNeverExceedsTheOrderTotal() {
        CouponRules.Quote quote =
                CouponRules.quote(snapshot("flat", "40.00", null, null), 0, 0, new BigDecimal("80"), null, NOW);
        assertThat(quote.discount()).isEqualByComparingTo("40.00");
        assertThat(quote.payable()).isEqualByComparingTo("40.00");

        CouponRules.Quote capped =
                CouponRules.quote(snapshot("flat", "100.00", null, null), 0, 0, new BigDecimal("80"), null, NOW);
        assertThat(capped.discount()).isEqualByComparingTo("80.00");
        assertThat(capped.payable()).isEqualByComparingTo("0.00");
    }

    @Test
    void quoteRejectsEachLimit() {
        assertThat(CouponRules.quote(paused(), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Coupon is not active");
        assertThat(CouponRules.quote(starting(NOW.plusSeconds(1)), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Coupon has not started");
        assertThat(CouponRules.quote(ending(NOW), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Coupon has expired");
        assertThat(CouponRules.quote(ending(NOW.plusSeconds(1)), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isNull();
        assertThat(CouponRules.quote(selected(PHARMACY), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Choose a pharmacy");
        assertThat(CouponRules.quote(selected(PHARMACY), 0, 0, new BigDecimal("500"), UUID.randomUUID(), NOW)
                        .problem())
                .isEqualTo("Coupon does not apply at this pharmacy");
        assertThat(CouponRules.quote(selected(PHARMACY), 0, 0, new BigDecimal("500"), PHARMACY, NOW)
                        .problem())
                .isNull();
        assertThat(CouponRules.quote(uses(2, null), 2, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Coupon has reached its use limit");
        assertThat(CouponRules.quote(uses(null, 1), 0, 1, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("You have already used this coupon");
        assertThat(CouponRules.quote(snapshot("pct", "10.00", null, "600.00"), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isEqualTo("Order total is below the minimum");
        assertThat(CouponRules.quote(snapshot("pct", "10.00", null, "500.00"), 0, 0, new BigDecimal("500"), null, NOW)
                        .problem())
                .isNull();
    }

    private static ObjectNode body() throws Exception {
        return (ObjectNode)
                JSON.readTree(
                        """
                {"code":"welcome10","title":"Welcome offer","discountType":"pct","discountValue":10}
                """);
    }

    private static CouponRules.Snapshot snapshot(String type, String value, String maxDiscount, String minOrder) {
        return new CouponRules.Snapshot(
                "WELCOME10",
                "Welcome offer",
                type,
                new BigDecimal(value),
                maxDiscount == null ? null : new BigDecimal(maxDiscount),
                minOrder == null ? null : new BigDecimal(minOrder),
                null,
                null,
                null,
                null,
                "all",
                List.of(),
                true);
    }

    private static CouponRules.Snapshot paused() {
        CouponRules.Snapshot open = snapshot("pct", "10.00", null, null);
        return new CouponRules.Snapshot(
                open.code(),
                open.title(),
                open.discountType(),
                open.discountValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                "all",
                List.of(),
                false);
    }

    private static CouponRules.Snapshot starting(Instant startsAt) {
        return new CouponRules.Snapshot(
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                null,
                startsAt,
                null,
                null,
                null,
                "all",
                List.of(),
                true);
    }

    private static CouponRules.Snapshot ending(Instant endsAt) {
        return new CouponRules.Snapshot(
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                endsAt,
                null,
                null,
                "all",
                List.of(),
                true);
    }

    private static CouponRules.Snapshot selected(UUID pharmacyId) {
        return new CouponRules.Snapshot(
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                "selected",
                List.of(pharmacyId),
                true);
    }

    private static CouponRules.Snapshot uses(Integer total, Integer perCustomer) {
        return new CouponRules.Snapshot(
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                null,
                total,
                perCustomer,
                "all",
                List.of(),
                true);
    }
}
