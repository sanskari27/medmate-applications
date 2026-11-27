package in.medmate.pharmacy_server.cart.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.cart.store.CartStore;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.coupons.web.CouponService;
import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CartControllerTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    private final CartStore carts = Mockito.mock(CartStore.class);
    private final AddressStore addresses = Mockito.mock(AddressStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartController(new CartService(
                    carts,
                    addresses,
                    Mockito.mock(NearbyStore.class),
                    Mockito.mock(StockStore.class),
                    Mockito.mock(CatalogStore.class),
                    Mockito.mock(CouponService.class),
                    Clock.fixed(Instant.parse("2026-11-28T00:00:00Z"), ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void aPharmacyCannotReadTheCustomerCart() throws Exception {
        mvc.perform(get("/api/v1/customer/cart").principal(account(AccountType.PHARMACY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
    }

    @Test
    void anEmptyCartAsksForADeliveryAddress() throws Exception {
        when(carts.find(CUSTOMER)).thenReturn(Optional.empty());
        when(addresses.selected(CUSTOMER)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/customer/cart").principal(account(AccountType.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.selectionSource").value("recommended"))
                .andExpect(jsonPath("$.notice").value("Select a delivery address"));
    }

    private static AccountAuthentication account(AccountType type) {
        AccountPrincipal principal = new AccountPrincipal(
                CUSTOMER,
                "+919845012345",
                type,
                "Priya",
                false,
                type == AccountType.PHARMACY ? UUID.randomUUID() : null,
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
