package in.medmate.pharmacy_server.customer.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.NearbyRules;
import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.identity.pharmacy.PharmacyRules;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerCatalogControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID NEAR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SAME = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID FAR = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID CLOSED_SHOP = UUID.fromString("00000000-0000-4000-8000-000000000004");

    private final CatalogStore catalog = Mockito.mock(CatalogStore.class);
    private final StockStore stock = Mockito.mock(StockStore.class);
    private final AddressStore addresses = Mockito.mock(AddressStore.class);
    private final NearbyStore nearby = Mockito.mock(NearbyStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomerCatalogController(
                    new CustomerCatalogService(catalog, stock, addresses, nearby, Clock.fixed(NOW, ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void onlyACustomerCanListAvailableProducts() throws Exception {
        when(stock.availableProductIds("2026-11")).thenReturn(List.of("IN1"));
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.empty());
        when(catalog.available(anyList(), eq("Pain"), eq("Crocin"), eq(1), eq(25)))
                .thenReturn(new CatalogDocuments.AvailablePage(
                        List.of(new CatalogDocuments.AvailableItem(
                                "IN1",
                                "Crocin Advance",
                                "Crocin",
                                new BigDecimal("25.50"),
                                "https://img.example/crocin.jpg",
                                true,
                                "GSK",
                                "Strip of 15 tablets",
                                "Tablet",
                                "Paracetamol (650mg)")),
                        1,
                        25,
                        1));

        mvc.perform(get("/api/v1/customer/catalog")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/catalog").principal(account(AccountType.PHARMACY, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));

        mvc.perform(get("/api/v1/customer/catalog")
                        .principal(account(AccountType.CUSTOMER, false))
                        .param("category", " Pain ")
                        .param("q", " Crocin "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value("IN1"))
                .andExpect(jsonPath("$.items[0].name").value("Crocin Advance"))
                .andExpect(jsonPath("$.items[0].displayName").value("Crocin"))
                .andExpect(jsonPath("$.items[0].price").value(25.50))
                .andExpect(jsonPath("$.items[0].image").value("https://img.example/crocin.jpg"))
                .andExpect(jsonPath("$.items[0].rxRequired").value(true))
                .andExpect(jsonPath("$.items[0].marketerName").value("GSK"))
                .andExpect(jsonPath("$.items[0].pack").value("Strip of 15 tablets"))
                .andExpect(jsonPath("$.items[0].productForm").value("Tablet"))
                .andExpect(jsonPath("$.items[0].composition").value("Paracetamol (650mg)"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aShortSearchNeverReadsStock() throws Exception {
        mvc.perform(get("/api/v1/customer/catalog")
                        .principal(account(AccountType.CUSTOMER, false))
                        .param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Query is not valid"));
        verify(stock, never()).availableProductIds(Mockito.any());
        verify(catalog, never()).available(anyList(), Mockito.any(), Mockito.any(), anyInt(), anyInt());
    }

    @Test
    void aCustomerCanReadADrugWithNearbyOffersAndSimilarProducts() throws Exception {
        when(stock.availableProductIds("2026-11")).thenReturn(List.of("MOOV1", "S0"));
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.of(address(12.97, 77.59)));
        when(catalog.findMany(List.of("MOOV1")))
                .thenReturn(new CatalogStore.Found(List.of(drug()), List.of(gel()), List.of()));
        when(nearby.nearby(12.97, 77.59, NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of(
                        pharmacy(FAR, "Lakshmi Pharma", 4.2, true, null, true),
                        pharmacy(SAME, "Sri Sai Medicals", 0.8, true, "KA-2", false),
                        pharmacy(NEAR, "HealthFirst Chemist", 0.8, true, "KA-1", false),
                        pharmacy(CLOSED_SHOP, "Night Counter", 1.1, false, "KA-9", false)));
        when(nearby.within(12.97, 77.59, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of(pharmacy(NEAR, "HealthFirst Chemist", 0.8, true, "KA-1", false)));
        when(stock.onlineListings(anyList(), anyList(), eq("2026-11")))
                .thenReturn(List.of(
                        new StockStore.OnlineListing(FAR, "MOOV1", new BigDecimal("44.00"), new BigDecimal("40.00"), 2),
                        new StockStore.OnlineListing(
                                SAME, "MOOV1", new BigDecimal("44.00"), new BigDecimal("39.00"), 0),
                        new StockStore.OnlineListing(
                                SAME, "MOOV1", new BigDecimal("44.00"), new BigDecimal("39.00"), 3),
                        new StockStore.OnlineListing(
                                NEAR, "MOOV1", new BigDecimal("44.00"), new BigDecimal("38.00"), 4),
                        new StockStore.OnlineListing(
                                CLOSED_SHOP, "MOOV1", new BigDecimal("44.00"), new BigDecimal("38.00"), 1),
                        new StockStore.OnlineListing(
                                NEAR, "OTHER", new BigDecimal("10.00"), new BigDecimal("9.00"), 1)));
        when(catalog.available(anyList(), eq("Fever & Pain"), eq(""), eq(1), eq(9)))
                .thenReturn(similarPage());

        mvc.perform(get("/api/v1/customer/catalog/MOOV1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/catalog/MOOV1").principal(account(AccountType.PHARMACY, false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/catalog/MOOV1").principal(account(AccountType.CUSTOMER, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("MOOV1"))
                .andExpect(jsonPath("$.name").value("Moov Cool Therapy Gel"))
                .andExpect(jsonPath("$.displayName").value("Moov"))
                .andExpect(jsonPath("$.marketerName").value("Reckitt"))
                .andExpect(jsonPath("$.pack").value("Tube of 10 gm Gel"))
                .andExpect(jsonPath("$.productForm").value("Gel"))
                .andExpect(jsonPath("$.composition").value("Diclofenac (1% w/w)"))
                .andExpect(jsonPath("$.rxRequired").value(false))
                .andExpect(jsonPath("$.categories[0]").value("Fever & Pain"))
                .andExpect(jsonPath("$.images[0]").value("https://img.example/moov.png"))
                .andExpect(jsonPath("$.introduction").value("Soothes pain"))
                .andExpect(jsonPath("$.benefits").value("Cooling relief"))
                .andExpect(jsonPath("$.howToUse").value("Apply a thin layer"))
                .andExpect(jsonPath("$.ifMiss").value("Apply when you remember"))
                .andExpect(jsonPath("$.storage").value("Keep cool"))
                .andExpect(jsonPath("$.mrp").value("44.00"))
                .andExpect(jsonPath("$.uses[0]").value("Muscle pain"))
                .andExpect(jsonPath("$.sideEffects[0]").value("Mild burning"))
                .andExpect(jsonPath("$.safety[0].topic").value("Pregnancy"))
                .andExpect(jsonPath("$.safety[0].level").value("caution"))
                .andExpect(jsonPath("$.safety[0].detail").value("Ask a doctor"))
                .andExpect(jsonPath("$.questions[0].question").value("How often?"))
                .andExpect(jsonPath("$.questions[0].answer").value("Twice a day"))
                .andExpect(jsonPath("$.offers[0].pharmacyId").value(NEAR.toString()))
                .andExpect(jsonPath("$.offers[0].pharmacyName").value("HealthFirst Chemist"))
                .andExpect(jsonPath("$.offers[0].distanceKm").value(0.8))
                .andExpect(jsonPath("$.offers[0].onlinePrice").value("38.00"))
                .andExpect(jsonPath("$.offers[0].onlineMrp").value("44.00"))
                .andExpect(jsonPath("$.offers[0].deliveryCharge.amount").value("0.00"))
                .andExpect(jsonPath("$.offers[0].licenceNumber").value("KA-1"))
                .andExpect(jsonPath("$.offers[0].pharmacistName").value("Meena Rao"))
                .andExpect(jsonPath("$.offers[0].city").value("Bengaluru"))
                .andExpect(jsonPath("$.offers[0].closesOn").value("21:00"))
                .andExpect(jsonPath("$.offers[0].closed").value(false))
                .andExpect(jsonPath("$.offers[1].pharmacyId").value(SAME.toString()))
                .andExpect(jsonPath("$.offers[1].deliveryCharge.amount").value("0.00"))
                .andExpect(jsonPath("$.offers[2].pharmacyName").value("Lakshmi Pharma"))
                .andExpect(jsonPath("$.offers[2].onlinePrice").value("40.00"))
                .andExpect(jsonPath("$.offers[2].deliveryCharge.amount").value("20.00"))
                .andExpect(jsonPath("$.offers[2].licenceNumber").value(nullValue()))
                .andExpect(jsonPath("$.offers[2].closesOn").value(nullValue()))
                .andExpect(jsonPath("$.offers[2].closed").value(true))
                .andExpect(jsonPath("$.similar.length()").value(8))
                .andExpect(jsonPath("$.similar[0].productId").value("S0"))
                .andExpect(jsonPath("$.similar[0].displayName").value("Volini"))
                .andExpect(jsonPath("$.similar[7].productId").value("S7"));
        verify(catalog, never()).findMany(List.of(" "));
    }

    @Test
    void aMissingOrUnstockedProductIsNotFound() throws Exception {
        when(stock.availableProductIds("2026-11")).thenReturn(List.of("MOOV1"));
        when(catalog.findMany(List.of("GONE")))
                .thenReturn(new CatalogStore.Found(List.of(), List.of(), List.of("GONE")));

        mvc.perform(get("/api/v1/customer/catalog/ ").principal(account(AccountType.CUSTOMER, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product was not found"));
        mvc.perform(get("/api/v1/customer/catalog/OTHER").principal(account(AccountType.CUSTOMER, false)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customer/catalog/GONE").principal(account(AccountType.CUSTOMER, false)))
                .andExpect(status().isNotFound());
        verify(catalog, never()).findMany(List.of("OTHER"));
    }

    @Test
    void anFmcgProductUsesItsOwnCopyAndSkipsSimilarWhenItHasNoCategory() throws Exception {
        when(stock.availableProductIds("2026-11")).thenReturn(List.of("SOAP1"));
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.of(address(null, null)));
        when(catalog.findMany(List.of("SOAP1")))
                .thenReturn(new CatalogStore.Found(List.of(), List.of(soap()), List.of()));

        mvc.perform(get("/api/v1/customer/catalog/SOAP1").principal(account(AccountType.CUSTOMER, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Savlon Soap"))
                .andExpect(jsonPath("$.displayName").value(nullValue()))
                .andExpect(jsonPath("$.pack").value("75 g pack"))
                .andExpect(jsonPath("$.composition").value("Glycerin"))
                .andExpect(jsonPath("$.rxRequired").value(false))
                .andExpect(jsonPath("$.introduction").value("Cleans skin"))
                .andExpect(jsonPath("$.benefits").value("Softens skin"))
                .andExpect(jsonPath("$.howToUse").value("Lather and rinse"))
                .andExpect(jsonPath("$.ifMiss").value(nullValue()))
                .andExpect(jsonPath("$.mrp").value(nullValue()))
                .andExpect(jsonPath("$.uses[0]").value("Daily wash"))
                .andExpect(jsonPath("$.safety[0].topic").value(nullValue()))
                .andExpect(jsonPath("$.safety[0].detail").value("For external use"))
                .andExpect(jsonPath("$.questions.length()").value(0))
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.similar.length()").value(0));
        verify(catalog, never()).available(anyList(), Mockito.any(), Mockito.any(), anyInt(), anyInt());
        verify(nearby, never()).nearby(anyDouble(), anyDouble(), anyInt(), anyDouble());
    }

    private static AddressStore.AddressRow address(Double latitude, Double longitude) {
        return new AddressStore.AddressRow(
                CUSTOMER,
                "Home",
                "1 Road",
                null,
                null,
                "Bengaluru",
                "560011",
                latitude,
                longitude,
                null,
                null,
                false,
                false,
                false,
                false,
                null,
                true,
                NOW,
                NOW);
    }

    private static NearbyStore.NearbyRow pharmacy(
            UUID id, String name, double distanceKm, boolean online, String licence, boolean closed) {
        return new NearbyStore.NearbyRow(
                id,
                name,
                "BTM Layout",
                "Bengaluru",
                "KA",
                "560076",
                12.9,
                77.6,
                distanceKm,
                online,
                licence,
                "Meena Rao",
                null,
                null,
                List.of(new PharmacyRules.OpeningHour("friday", closed, "09:00", "21:00")));
    }

    private static CatalogDocuments.AvailablePage similarPage() {
        List<CatalogDocuments.AvailableItem> items = new ArrayList<>();
        items.add(available("MOOV1", "Moov"));
        for (int index = 0; index < 9; index++) {
            items.add(available("S" + index, "Volini"));
        }
        return new CatalogDocuments.AvailablePage(items, 1, 9, items.size());
    }

    private static CatalogDocuments.AvailableItem available(String id, String name) {
        return new CatalogDocuments.AvailableItem(
                id, name, name, new BigDecimal("71"), null, false, null, null, null, null);
    }

    private static CatalogDocuments.DrugDetail drug() {
        return new CatalogDocuments.DrugDetail(
                "MOOV1",
                "Moov Cool Therapy Gel",
                " Moov ",
                " Reckitt ",
                null,
                "OTC",
                " Soothes pain ",
                " Cooling relief ",
                " Apply a thin layer ",
                " Apply when you remember ",
                "Tube",
                " Tube of 10 gm Gel ",
                null,
                null,
                null,
                " Gel ",
                new BigDecimal("44"),
                false,
                " Keep cool ",
                null,
                "India",
                null,
                List.of(" Fever & Pain ", " "),
                List.of(
                        new CatalogDocuments.DrugIngredient(" ", null, null, null),
                        new CatalogDocuments.DrugIngredient("Diclofenac", " 1% w/w ", null, null)),
                List.of(
                        new CatalogDocuments.DrugSafety("Pregnancy", " caution ", " "),
                        new CatalogDocuments.DrugSafety("Pregnancy", " caution ", " Ask a doctor ")),
                List.of(),
                List.of(" Muscle pain "),
                List.of(" Mild burning "),
                List.of(
                        new CatalogDocuments.DrugQuestion("How often?", " "),
                        new CatalogDocuments.DrugQuestion(" How often? ", " Twice a day ")),
                List.of(),
                List.of(" https://img.example/moov.png ", " "));
    }

    private static CatalogDocuments.FmcgDetail gel() {
        return new CatalogDocuments.FmcgDetail(
                "MOOV1",
                "Ignored gel",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static CatalogDocuments.FmcgDetail soap() {
        return new CatalogDocuments.FmcgDetail(
                "SOAP1",
                "Savlon Soap",
                " ",
                "ITC",
                "Soap",
                "75 g pack",
                " ",
                null,
                null,
                null,
                "Bar",
                null,
                " Cleans skin ",
                " Lather and rinse ",
                "India",
                " ITC ",
                null,
                null,
                List.of(" "),
                List.of(" Daily wash "),
                List.of(" Glycerin ", " "),
                List.of(" Softens skin "),
                List.of(" For external use ", " "),
                List.of());
    }

    private static AccountAuthentication account(AccountType type, boolean disabled) {
        AccountPrincipal principal = new AccountPrincipal(
                CUSTOMER,
                "+919845012345",
                type,
                "Priya",
                disabled,
                null,
                type == AccountType.PHARMACY ? PharmacyRole.OWNER : null,
                type == AccountType.PHARMACY ? VerificationStatus.APPROVED : null,
                null,
                null,
                Set.of(),
                Set.of(),
                true,
                null);
        return new AccountAuthentication(principal);
    }
}
