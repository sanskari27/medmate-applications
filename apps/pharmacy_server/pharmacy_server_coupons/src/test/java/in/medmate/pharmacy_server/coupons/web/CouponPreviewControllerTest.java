package in.medmate.pharmacy_server.coupons.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.coupons.store.CouponStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.StaffSubtype;
import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import in.medmate.pharmacy_server.identity.documents.DocumentStore;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CouponPreviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID COUPON = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID PHARMACY = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    private final CouponStore store = Mockito.mock(CouponStore.class);
    private final DocumentStore documents = Mockito.mock(DocumentStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CouponController(new CouponService(
                    store,
                    documents,
                    new MedmateIdentityProperties(null, null, null, null, null),
                    Clock.fixed(NOW, ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void aCustomerReceivesTheDiscountAndBanner() throws Exception {
        when(store.findByCode("WELCOME10")).thenReturn(Optional.of(open()));
        when(documents.url(any())).thenReturn("https://files.example/banner.png");

        mvc.perform(post("/api/v1/customer/coupons/preview")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"welcome10\",\"orderTotal\":\"500\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WELCOME10"))
                .andExpect(jsonPath("$.discount").value("50.00"))
                .andExpect(jsonPath("$.payable").value("450.00"))
                .andExpect(jsonPath("$.banner.url").value("https://files.example/banner.png"));
    }

    @Test
    void staffAndGuestsCannotPreview() throws Exception {
        mvc.perform(post("/api/v1/customer/coupons/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WELCOME10\",\"orderTotal\":\"500\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in is required"));
        mvc.perform(post("/api/v1/customer/coupons/preview")
                        .principal(new AccountAuthentication(new AccountPrincipal(
                                CUSTOMER,
                                "919800000000",
                                AccountType.STAFF,
                                "Ada",
                                false,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Set.of(StaffSubtype.SALES),
                                Set.of(),
                                true,
                                null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WELCOME10\",\"orderTotal\":\"500\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
    }

    @Test
    void previewExplainsWhyACouponDoesNotApply() throws Exception {
        expect("Coupon is not recognised", Optional.empty(), 0, "{\"code\":\"NOPE\",\"orderTotal\":100}");
        expect("Coupon is not active", Optional.of(copy(false, null, null, null, null, "all", 0)), 0, order());
        expect(
                "Coupon has not started",
                Optional.of(copy(true, NOW.plusSeconds(60), null, null, null, "all", 0)),
                0,
                order());
        expect("Coupon has expired", Optional.of(copy(true, null, NOW, null, null, "all", 0)), 0, order());
        expect("Coupon has reached its use limit", Optional.of(copy(true, null, null, 1, null, "all", 1)), 0, order());
        expect("You have already used this coupon", Optional.of(copy(true, null, null, null, 1, "all", 0)), 1, order());
        expect(
                "Order total is below the minimum",
                Optional.of(copy(true, null, null, null, null, "all", 0, new BigDecimal("1000.00"))),
                0,
                order());
        when(store.pharmacyIds(COUPON)).thenReturn(List.of(PHARMACY));
        expect("Choose a pharmacy", Optional.of(copy(true, null, null, null, null, "selected", 0)), 0, order());
        expect(
                "Coupon does not apply at this pharmacy",
                Optional.of(copy(true, null, null, null, null, "selected", 0)),
                0,
                "{\"code\":\"WELCOME10\",\"orderTotal\":\"500\",\"pharmacyId\":\"" + UUID.randomUUID() + "\"}");
    }

    private void expect(String detail, Optional<CouponStore.CouponRow> row, int usedByCustomer, String body)
            throws Exception {
        when(store.findByCode("WELCOME10")).thenReturn(row);
        when(store.findByCode("NOPE")).thenReturn(Optional.empty());
        when(store.redemptionsForCustomer(COUPON, CUSTOMER)).thenReturn(usedByCustomer);
        mvc.perform(post("/api/v1/customer/coupons/preview")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));
    }

    private static String order() {
        return "{\"code\":\"welcome10\",\"orderTotal\":\"500\"}";
    }

    private static CouponStore.CouponRow open() {
        return copy(true, null, null, null, null, "all", 0);
    }

    private static CouponStore.CouponRow copy(
            boolean active,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String scope,
            int redemptions) {
        return copy(active, startsAt, endsAt, maxRedemptions, maxPerCustomer, scope, redemptions, null);
    }

    private static CouponStore.CouponRow copy(
            boolean active,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String scope,
            int redemptions,
            BigDecimal minOrder) {
        return new CouponStore.CouponRow(
                COUPON,
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                minOrder,
                startsAt,
                endsAt,
                maxRedemptions,
                maxPerCustomer,
                scope,
                active,
                "app-uploads/medmate-server/coupon-banners/" + COUPON,
                "image/png",
                NOW,
                NOW,
                redemptions,
                0);
    }

    private static AccountAuthentication customer() {
        return new AccountAuthentication(new AccountPrincipal(
                CUSTOMER,
                "919845012345",
                AccountType.CUSTOMER,
                "Priya",
                false,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                true,
                null));
    }
}
