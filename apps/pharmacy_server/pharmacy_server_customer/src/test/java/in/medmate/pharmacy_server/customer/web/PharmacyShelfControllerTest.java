package in.medmate.pharmacy_server.customer.web;

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
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

class PharmacyShelfControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:30:00Z");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID NEAR = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String SHELF = "/api/v1/customer/pharmacies/" + NEAR + "/catalog";

    private final CatalogStore catalog = Mockito.mock(CatalogStore.class);
    private final StockStore stock = Mockito.mock(StockStore.class);
    private final AddressStore addresses = Mockito.mock(AddressStore.class);
    private final NearbyStore nearby = Mockito.mock(NearbyStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PharmacyShelfController(new PharmacyShelfService(
                    catalog,
                    stock,
                    addresses,
                    nearby,
                    Clock.fixed(
                            LocalDateTime.of(2026, 11, 23, 16, 0)
                                    .atZone(ZoneId.of("Asia/Kolkata"))
                                    .toInstant(),
                            ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void onlyACustomerWithANearbyOnlinePharmacyCanReadItsShelf() throws Exception {
        mvc.perform(get(SHELF)).andExpect(status().isUnauthorized());
        mvc.perform(get(SHELF).principal(account(AccountType.PHARMACY, false))).andExpect(status().isForbidden());
        mvc.perform(get(SHELF).principal(account(AccountType.CUSTOMER, true))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/pharmacies/not-a-uuid/catalog").principal(customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pharmacy was not found"));

        when(addresses.selected(CUSTOMER)).thenReturn(Optional.empty());
        mvc.perform(get(SHELF).principal(customer())).andExpect(status().isNotFound());
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.of(address(null, 77.59)));
        mvc.perform(get(SHELF).principal(customer())).andExpect(status().isNotFound());
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.of(address(12.9716, 77.5946)));
        when(nearby.nearby(12.9716, 77.5946, NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of(pharmacy(false)));
        mvc.perform(get(SHELF).principal(customer())).andExpect(status().isNotFound());
        when(nearby.nearby(12.9716, 77.5946, NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of());
        mvc.perform(get(SHELF).principal(customer())).andExpect(status().isNotFound());
        verify(stock, never()).pharmacyListings(eq(NEAR), eq("2026-11"));
    }

    @Test
    void theShelfPreviewsEachCategoryAndCanOpenOne() throws Exception {
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.of(address(12.9716, 77.5946)));
        when(nearby.nearby(12.9716, 77.5946, NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of(pharmacy(true)));
        List<StockStore.OnlineListing> listings = new ArrayList<>();
        List<CatalogDocuments.AvailableItem> items = new ArrayList<>();
        List<CatalogDocuments.ProductCategory> categories = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            String id = "E" + index;
            listings.add(listing(id, "100.00", "80.50", 2));
            items.add(item(id, index == 0 ? "  " : "Soap " + index, "  ", " ", " ", false));
            categories.add(new CatalogDocuments.ProductCategory(id, "Everyday essentials"));
        }
        listings.add(listing("E0", "1.00", "1.00", 4));
        listings.add(listing("F0", "120.00", "99.00", 1));
        listings.add(listing("F1", "100.00", "80.00", 1));
        listings.add(listing("F2", "50.00", "40.00", 1));
        listings.add(listing("F3", "30.00", "25.00", 1));
        listings.add(listing("B1", "80.00", "70.00", 1));
        listings.add(listing("U1", "15.00", "12.00", 1));
        listings.add(listing("GONE", null, "1.00", 1));
        listings.add(listing("EMPTY", "1.00", "1.00", 0));
        listings.add(listing("UNLISTED", "10.00", "9.00", 1));
        items.add(item("F0", "Diclofenac", "Diclofenac Gel", "https://cdn.example/f.png", "30 g", true));
        items.add(item("F1", "Dolo", "Dolo 650", null, "15 tablets", false));
        items.add(item("F2", "Crocin", "Crocin", null, null, false));
        items.add(item("F3", "Combiflam", "Combiflam", null, "20 tablets", false));
        items.add(item("B1", "Baby wash", "Baby wash", null, "200 ml", false));
        items.add(item("U1", "Cotton", "Cotton", null, null, false));
        items.add(item("EXTRA", "Extra", "Extra", null, null, false));
        categories.add(new CatalogDocuments.ProductCategory("F0", "Fever & Pain"));
        categories.add(new CatalogDocuments.ProductCategory("F1", "Fever & Pain"));
        categories.add(new CatalogDocuments.ProductCategory("F2", "Fever & Pain"));
        categories.add(new CatalogDocuments.ProductCategory("F3", "Fever & Pain"));
        categories.add(new CatalogDocuments.ProductCategory("B1", "Baby care"));
        categories.add(new CatalogDocuments.ProductCategory("U1", "  "));
        when(stock.pharmacyListings(NEAR, "2026-11")).thenReturn(listings);
        when(catalog.primaryCategories(anyList())).thenReturn(categories);
        when(catalog.available(anyList(), eq(""), eq(""), eq(1), anyInt()))
                .thenReturn(new CatalogDocuments.AvailablePage(items, 1, items.size(), items.size()));
        when(catalog.available(anyList(), eq(""), eq("dolo"), eq(1), anyInt()))
                .thenReturn(new CatalogDocuments.AvailablePage(List.of(items.get(11)), 1, 1, 1));

        mvc.perform(get(SHELF).principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Everyday essentials"))
                .andExpect(jsonPath("$.categories[0].total").value(10))
                .andExpect(jsonPath("$.categories[1].name").value("Baby care"))
                .andExpect(jsonPath("$.categories[2].name").value("Fever & Pain"))
                .andExpect(jsonPath("$.categories[2].total").value(4))
                .andExpect(jsonPath("$.categories[3].name").value("Other"))
                .andExpect(jsonPath("$.total").value(16))
                .andExpect(jsonPath("$.items.length()").value(9 + 1 + 3 + 1))
                .andExpect(jsonPath("$.items[0].productId").value("E0"))
                .andExpect(jsonPath("$.items[0].name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].displayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].image").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].pack").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].onlineMrp").value("100.00"))
                .andExpect(jsonPath("$.items[0].onlinePrice").value("80.50"))
                .andExpect(jsonPath("$.page").value(1));

        mvc.perform(get(SHELF).principal(customer()).param("rx", "required"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Fever & Pain"))
                .andExpect(jsonPath("$.items[0].productId").value("F0"))
                .andExpect(jsonPath("$.items[0].rxRequired").value(true))
                .andExpect(jsonPath("$.items[0].image").value("https://cdn.example/f.png"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(SHELF).principal(customer()).param("rx", " OTC "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[2].name").value("Fever & Pain"))
                .andExpect(jsonPath("$.categories[2].total").value(3));
        mvc.perform(get(SHELF).principal(customer()).param("rx", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Prescription filter is not valid"));

        mvc.perform(get(SHELF).principal(customer()).param("q", " dolo "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value("F1"))
                .andExpect(jsonPath("$.items[0].displayName").value("Dolo 650"))
                .andExpect(jsonPath("$.items[0].pack").value("15 tablets"));

        mvc.perform(get(SHELF)
                        .principal(customer())
                        .param("category", " fever & pain ")
                        .param("page", "2")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value("F3"))
                .andExpect(jsonPath("$.categories.length()").value(4));
        mvc.perform(get(SHELF)
                        .principal(customer())
                        .param("category", "Fever & Pain")
                        .param("page", "3")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get(SHELF).principal(customer()).param("category", "Missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.size").value(25));
        mvc.perform(get(SHELF)
                        .principal(customer())
                        .param("category", "Baby care")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Page is not valid"));

        when(stock.pharmacyListings(NEAR, "2026-11")).thenReturn(List.of());
        mvc.perform(get(SHELF).principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(SHELF)
                        .principal(customer())
                        .param("category", "Baby care")
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(0));
        verify(catalog, never()).available(anyList(), eq(""), eq("absent"), eq(1), anyInt());
    }

    private static StockStore.OnlineListing listing(String productId, String mrp, String price, int qty) {
        return new StockStore.OnlineListing(
                NEAR,
                productId,
                mrp == null ? null : new BigDecimal(mrp),
                price == null ? null : new BigDecimal(price),
                qty);
    }

    private static CatalogDocuments.AvailableItem item(
            String id, String name, String display, String image, String pack, boolean rx) {
        return new CatalogDocuments.AvailableItem(
                id, name, display, new BigDecimal("10.00"), image, rx, "Maker", pack, "tablet", "salt");
    }

    private static NearbyStore.NearbyRow pharmacy(boolean online) {
        return new NearbyStore.NearbyRow(
                NEAR,
                "HealthFirst Chemist",
                "BTM Layout",
                "Bengaluru",
                "Karnataka",
                "560076",
                12.97,
                77.59,
                0.8,
                online,
                "KA-BLR-20B/21B 118744",
                "Meena Rao",
                null,
                null,
                List.of());
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

    private static AccountAuthentication customer() {
        return account(AccountType.CUSTOMER, false);
    }

    private static AccountAuthentication account(AccountType type, boolean disabled) {
        return new AccountAuthentication(new AccountPrincipal(
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
                null));
    }
}
