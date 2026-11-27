package in.medmate.pharmacy_server.coupons.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.coupons.CouponRules;
import in.medmate.pharmacy_server.coupons.store.CouponStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.StaffSubtype;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CouponControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID COUPON = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final String JSON =
            """
            {"code":"welcome10","title":"Welcome offer","discountType":"pct","discountValue":10}
            """;

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
    void onlyAdminAndSalesCanManageCoupons() throws Exception {
        when(store.page(anyInt(), anyInt())).thenReturn(List.of(row(true, "all", null, null, 0)));
        when(store.count()).thenReturn(1);
        when(documents.url(any())).thenReturn("https://files.example/banner.png");

        mvc.perform(get("/api/v1/coupons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/coupons").principal(staff(StaffSubtype.FINANCE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
        mvc.perform(get("/api/v1/coupons").principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].code").value("WELCOME10"))
                .andExpect(jsonPath("$.coupons[0].banner.url").value("https://files.example/banner.png"));
        mvc.perform(get("/api/v1/coupons").principal(account(AccountType.ADMIN, Set.of(), false)))
                .andExpect(status().isOk());
    }

    @Test
    void createRequiresABannerImage() throws Exception {
        when(store.pharmaciesExist(any())).thenReturn(true);
        mvc.perform(multipart("/api/v1/coupons").file(couponPart()).principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Banner is required"));

        mvc.perform(multipart("/api/v1/coupons")
                        .file(couponPart())
                        .file(banner("text/plain", new byte[] {1}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Banner must be an image"));

        mvc.perform(multipart("/api/v1/coupons")
                        .file(couponPart())
                        .file(banner("image/png", new byte[CouponRules.BANNER_MAX + 1]))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Banner is too large"));
        verify(store, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void salesCanCreateACouponAndReplaceItsBanner() throws Exception {
        when(store.pharmaciesExist(any())).thenReturn(true);
        when(documents.url(any())).thenReturn("https://files.example/banner.png");
        when(store.insert(any(), any(), any(), eq("image/png"), eq(NOW))).thenReturn(row(true, "all", null, null, 0));

        mvc.perform(multipart("/api/v1/coupons")
                        .file(couponPart())
                        .file(banner("image/png", new byte[] {1, 2, 3}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coupon.code").value("WELCOME10"))
                .andExpect(jsonPath("$.coupon.banner.contentType").value("image/png"));
        verify(documents).put(any(), eq("image/png"), eq(new byte[] {1, 2, 3}));

        when(store.find(COUPON)).thenReturn(Optional.of(row(true, "all", null, null, 0)));
        when(store.update(eq(COUPON), any(), any(), eq("image/jpeg"), eq(NOW))).thenReturn(Optional.of(replaced()));
        when(documents.url(any())).thenReturn("https://files.example/next.jpg");

        MockMultipartHttpServletRequestBuilder put = multipart("/api/v1/coupons/" + COUPON);
        put.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        mvc.perform(put.file(couponPart())
                        .file(banner("image/jpeg", new byte[] {9}))
                        .principal(account(AccountType.ADMIN, Set.of(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.banner.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.coupon.banner.url").value("https://files.example/next.jpg"));
        verify(documents).delete("app-uploads/medmate-server/coupon-banners/" + COUPON);
    }

    @Test
    void aDuplicateCodeRemovesTheUploadedBanner() throws Exception {
        when(store.pharmaciesExist(any())).thenReturn(true);
        when(store.insert(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("coupon_code_unique"));

        mvc.perform(multipart("/api/v1/coupons")
                        .file(couponPart())
                        .file(banner("image/png", new byte[] {1}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Coupon code is already in use"));
        verify(documents).delete(any());
    }

    private static MockMultipartFile couponPart() {
        return new MockMultipartFile("coupon", "coupon.json", "application/json", JSON.getBytes());
    }

    private static MockMultipartFile banner(String contentType, byte[] body) {
        return new MockMultipartFile("banner", "banner.bin", contentType, body);
    }

    private static CouponStore.CouponRow replaced() {
        CouponStore.CouponRow row = row(true, "all", null, null, 0);
        return new CouponStore.CouponRow(
                row.id(),
                row.code(),
                row.title(),
                row.discountType(),
                row.discountValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                "all",
                true,
                row.bannerKey(),
                "image/jpeg",
                row.createdAt(),
                NOW,
                0,
                0);
    }

    private static CouponStore.CouponRow row(
            boolean active, String scope, Integer maxRedemptions, BigDecimal minOrder, int redemptions) {
        return new CouponStore.CouponRow(
                COUPON,
                "WELCOME10",
                "Welcome offer",
                "pct",
                new BigDecimal("10.00"),
                null,
                minOrder,
                null,
                null,
                maxRedemptions,
                null,
                scope,
                active,
                "app-uploads/medmate-server/coupon-banners/" + COUPON,
                "image/png",
                NOW,
                NOW,
                redemptions,
                0);
    }

    private static AccountAuthentication staff(StaffSubtype subtype) {
        return account(AccountType.STAFF, Set.of(subtype), false);
    }

    private static AccountAuthentication account(AccountType type, Set<StaffSubtype> subtypes, boolean disabled) {
        return new AccountAuthentication(new AccountPrincipal(
                UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
                "919800000000",
                type,
                "Ada",
                disabled,
                null,
                type == AccountType.PHARMACY ? PharmacyRole.OWNER : null,
                type == AccountType.PHARMACY ? VerificationStatus.APPROVED : null,
                null,
                null,
                subtypes,
                Set.of(),
                true,
                null));
    }
}
