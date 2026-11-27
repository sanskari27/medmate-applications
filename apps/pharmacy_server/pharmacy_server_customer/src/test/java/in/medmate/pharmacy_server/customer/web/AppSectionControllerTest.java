package in.medmate.pharmacy_server.customer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.store.AppSectionStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.StaffSubtype;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import in.medmate.pharmacy_server.identity.documents.DocumentStore;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppSectionControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID SECTION = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final String JSON = "{\"category\":\"Pain\",\"position\":1}";

    private final AppSectionStore store = Mockito.mock(AppSectionStore.class);
    private final CatalogStore catalog = Mockito.mock(CatalogStore.class);
    private final DocumentStore documents = Mockito.mock(DocumentStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AppSectionController(new AppSectionService(
                    store,
                    catalog,
                    documents,
                    new MedmateIdentityProperties(null, null, null, null, null),
                    Clock.fixed(NOW, ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void staffListSectionsAndCustomersReadTheSameTiles() throws Exception {
        when(store.list()).thenReturn(List.of(row()));
        when(documents.url(any())).thenReturn("https://files.example/pain.png");

        mvc.perform(get("/api/v1/app-sections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/app-sections").principal(staff(StaffSubtype.FINANCE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/app-sections").principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].category").value("Pain"))
                .andExpect(jsonPath("$.sections[0].position").value(1))
                .andExpect(jsonPath("$.sections[0].image.url").value("https://files.example/pain.png"))
                .andExpect(jsonPath("$.sections[0].image.contentType").value("image/png"));

        mvc.perform(get("/api/v1/customer/app-sections").principal(account(AccountType.CUSTOMER, Set.of(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].category").value("Pain"));
        mvc.perform(get("/api/v1/customer/app-sections").principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequiresAnImageOfAKnownCategory() throws Exception {
        mvc.perform(multipart("/api/v1/app-sections").file(sectionPart()).principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image is required"));

        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.empty());
        mvc.perform(multipart("/api/v1/app-sections")
                        .file(sectionPart())
                        .file(image("image/png", new byte[] {1, 2, 3}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category was not found"));
        verify(store, never()).insert(any(), any(), any(), any(), anyInt(), any());

        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.of("Pain"));
        when(store.insert(any(), eq("Pain"), any(), eq("image/png"), eq(1), eq(NOW)))
                .thenReturn(row());
        when(documents.url(any())).thenReturn("https://files.example/pain.png");
        mvc.perform(multipart("/api/v1/app-sections")
                        .file(sectionPart())
                        .file(image("image/png", new byte[] {1, 2, 3}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section.category").value("Pain"));
        verify(documents).put(any(), eq("image/png"), eq(new byte[] {1, 2, 3}));
    }

    @Test
    void aDuplicateCategoryIsRejectedAndDeleteRemovesTheImage() throws Exception {
        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.of("Pain"));
        when(store.insert(any(), eq("Pain"), any(), eq("image/png"), eq(1), eq(NOW)))
                .thenThrow(new DuplicateKeyException("category"));
        mvc.perform(multipart("/api/v1/app-sections")
                        .file(sectionPart())
                        .file(image("image/png", new byte[] {1}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is already listed"));
        verify(documents).delete(any());

        when(store.find(SECTION)).thenReturn(Optional.of(row()));
        when(store.delete(SECTION)).thenReturn(true);
        mvc.perform(delete("/api/v1/app-sections/" + SECTION).principal(account(AccountType.ADMIN, Set.of(), false)))
                .andExpect(status().isNoContent());
        verify(documents).delete("app-uploads/medmate-server/app-sections/" + SECTION);
    }

    private static MockMultipartFile sectionPart() {
        return new MockMultipartFile("section", "section.json", "application/json", JSON.getBytes());
    }

    private static MockMultipartFile image(String contentType, byte[] body) {
        return new MockMultipartFile("image", "pain.png", contentType, body);
    }

    private static AppSectionStore.SectionRow row() {
        return new AppSectionStore.SectionRow(
                SECTION, "Pain", "app-uploads/medmate-server/app-sections/" + SECTION, "image/png", 1, NOW, NOW);
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
