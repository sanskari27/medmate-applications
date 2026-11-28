package in.medmate.pharmacy_server.customer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppSectionControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID SECTION = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final UUID IMAGE = UUID.fromString("00000000-0000-4000-8000-0000000000d3");
    private static final String JSON =
            """
            {"title":"Everyday care","position":1,"categories":[{"category":"Pain","images":[{"file":true}]}]}
            """;
    private static final String IMAGE_KEY = "app-uploads/medmate-server/app-sections/" + SECTION + "/image";
    private static final String BANNER_KEY = "app-uploads/medmate-server/app-sections/" + SECTION + "/banner";
    private static final String CATEGORY_KEY = "app-uploads/medmate-server/app-sections/" + SECTION + "/pain";

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
    void staffListSectionsAndCustomersReadTheSameLayout() throws Exception {
        when(store.list()).thenReturn(List.of(row()));
        when(store.listCategories()).thenReturn(List.of(category()));
        when(store.listImages()).thenReturn(List.of(image()));
        when(documents.url(any())).thenReturn("https://files.example/pain.png");

        mvc.perform(get("/api/v1/app-sections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/app-sections").principal(staff(StaffSubtype.FINANCE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/app-sections").principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].title").value("Everyday care"))
                .andExpect(jsonPath("$.sections[0].position").value(1))
                .andExpect(jsonPath("$.sections[0].image.url").value("https://files.example/pain.png"))
                .andExpect(jsonPath("$.sections[0].banner.contentType").value("image/webp"))
                .andExpect(jsonPath("$.sections[0].categories[0].category").value("Pain"))
                .andExpect(jsonPath("$.sections[0].categories[0].images[0].id").value(IMAGE.toString()))
                .andExpect(jsonPath("$.sections[0].categories[0].images[0].contentType")
                        .value("image/jpeg"));

        mvc.perform(get("/api/v1/customer/app-sections").principal(account(AccountType.CUSTOMER, Set.of(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].title").value("Everyday care"))
                .andExpect(jsonPath("$.sections[0].categories[0].category").value("Pain"));
        mvc.perform(get("/api/v1/customer/app-sections").principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequiresSectionMediaAndAKnownCategoryImage() throws Exception {
        mvc.perform(multipart("/api/v1/app-sections").file(sectionPart()).principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image is required"));

        mvc.perform(multipart("/api/v1/app-sections")
                        .file(sectionPart())
                        .file(file("image", "image/png", new byte[] {1}))
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Banner is required"));

        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.empty());
        mvc.perform(createRequest().principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category was not found"));
        verify(store, never()).insert(any(), any(), any(), any(), any(), any(), anyInt(), any());

        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.of("Pain"));
        when(store.insert(any(), eq("Everyday care"), any(), eq("image/png"), any(), eq("image/webp"), eq(1), eq(NOW)))
                .thenAnswer(invocation -> new AppSectionStore.SectionRow(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        NOW,
                        NOW));
        when(documents.url(any())).thenReturn("https://files.example/pain.png");
        mvc.perform(createRequest().principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section.title").value("Everyday care"))
                .andExpect(jsonPath("$.section.banner.contentType").value("image/webp"))
                .andExpect(jsonPath("$.section.categories[0].category").value("Pain"))
                .andExpect(jsonPath("$.section.categories[0].images[0].contentType")
                        .value("image/jpeg"));
        verify(documents).put(any(), eq("image/png"), eq(new byte[] {1}));
        verify(documents).put(any(), eq("image/webp"), eq(new byte[] {2}));
        verify(documents).put(any(), eq("image/jpeg"), eq(new byte[] {3}));
    }

    @Test
    void duplicatesAreRejectedAndDeleteRemovesEveryImage() throws Exception {
        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.of("Pain"));
        when(store.insert(any(), eq("Everyday care"), any(), any(), any(), any(), eq(1), eq(NOW)))
                .thenThrow(new DuplicateKeyException("app_section_title_key"));
        mvc.perform(createRequest().principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Name is already listed"));
        verify(documents, times(3)).delete(any());

        when(store.insert(any(), eq("Everyday care"), any(), any(), any(), any(), eq(1), eq(NOW)))
                .thenReturn(row());
        doThrow(new DuplicateKeyException("app_section_category_name"))
                .when(store)
                .replaceCategories(any(), any(), eq(NOW));
        mvc.perform(createRequest().principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is already listed"));

        when(store.find(SECTION)).thenReturn(Optional.of(row()));
        when(store.categories(SECTION)).thenReturn(List.of(category()));
        when(store.images(any())).thenReturn(List.of(image()));
        when(store.delete(SECTION)).thenReturn(true);
        mvc.perform(delete("/api/v1/app-sections/" + SECTION).principal(account(AccountType.ADMIN, Set.of(), false)))
                .andExpect(status().isNoContent());
        verify(documents).delete(IMAGE_KEY);
        verify(documents).delete(BANNER_KEY);
        verify(documents).delete(CATEGORY_KEY);
    }

    @Test
    void updateKeepsACategoryImageAndReplacesTheBanner() throws Exception {
        when(store.find(SECTION)).thenReturn(Optional.of(row()));
        when(store.categories(SECTION)).thenReturn(List.of(category()));
        when(store.images(any())).thenReturn(List.of(image()));
        when(catalog.canonicalCategory("Pain")).thenReturn(Optional.of("Pain"));
        when(store.update(
                        any(),
                        eq("Everyday care"),
                        eq(IMAGE_KEY),
                        eq("image/png"),
                        any(),
                        eq("image/webp"),
                        eq(1),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(new AppSectionStore.SectionRow(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        NOW,
                        NOW)));
        when(documents.url(any())).thenReturn("https://files.example/pain.png");

        String kept =
                """
                {"title":"Everyday care","position":1,"categories":[{"category":"Pain","images":[{"id":"%s"}]}]}
                """
                        .formatted(IMAGE);
        mvc.perform(multipart("/api/v1/app-sections/" + SECTION)
                        .file(new MockMultipartFile("section", "section.json", "application/json", kept.getBytes()))
                        .file(file("banner", "image/webp", new byte[] {9}))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .principal(staff(StaffSubtype.SALES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.section.categories[0].images[0].id").value(IMAGE.toString()))
                .andExpect(jsonPath("$.section.banner.contentType").value("image/webp"));

        verify(documents).put(any(), eq("image/webp"), eq(new byte[] {9}));
        verify(documents).delete(BANNER_KEY);
        verify(documents, never()).delete(IMAGE_KEY);
        verify(documents, never()).delete(CATEGORY_KEY);
        verify(store).replaceCategories(eq(SECTION), any(), eq(NOW));
    }

    private static MockMultipartHttpServletRequestBuilder createRequest() {
        return multipart("/api/v1/app-sections")
                .file(sectionPart())
                .file(file("image", "image/png", new byte[] {1}))
                .file(file("banner", "image/webp", new byte[] {2}))
                .file(file("categoryImage", "image/jpeg", new byte[] {3}));
    }

    private static MockMultipartFile sectionPart() {
        return new MockMultipartFile("section", "section.json", "application/json", JSON.getBytes());
    }

    private static MockMultipartFile file(String name, String contentType, byte[] body) {
        return new MockMultipartFile(name, name + ".img", contentType, body);
    }

    private static AppSectionStore.SectionRow row() {
        return new AppSectionStore.SectionRow(
                SECTION, "Everyday care", IMAGE_KEY, "image/png", BANNER_KEY, "image/webp", 1, NOW, NOW);
    }

    private static AppSectionStore.CategoryRow category() {
        return new AppSectionStore.CategoryRow(CATEGORY, SECTION, "Pain", 0, NOW, NOW);
    }

    private static AppSectionStore.ImageRow image() {
        return new AppSectionStore.ImageRow(IMAGE, CATEGORY, CATEGORY_KEY, "image/jpeg", 0, NOW);
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
