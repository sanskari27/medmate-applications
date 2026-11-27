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
import java.time.ZoneOffset;
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
                                true)),
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
