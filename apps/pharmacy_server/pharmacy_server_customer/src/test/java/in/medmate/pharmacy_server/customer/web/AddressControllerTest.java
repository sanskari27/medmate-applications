package in.medmate.pharmacy_server.customer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AddressControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID ADDRESS = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    private final AddressStore store = Mockito.mock(AddressStore.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AddressController(new AddressService(store, Clock.fixed(NOW, ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void guestsAndPharmacyAccountsCannotReadAddresses() throws Exception {
        mvc.perform(get("/api/v1/customer/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in is required"));
        mvc.perform(get("/api/v1/customer/addresses").principal(account(AccountType.PHARMACY, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
        mvc.perform(get("/api/v1/customer/addresses").principal(account(AccountType.CUSTOMER, true)))
                .andExpect(status().isForbidden());
        verify(store, never()).list(any());
    }

    @Test
    void aCustomerListsAndCreatesASelectedAddress() throws Exception {
        when(store.list(CUSTOMER)).thenReturn(List.of(home(true)));
        mvc.perform(get("/api/v1/customer/addresses").principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].label").value("home"))
                .andExpect(jsonPath("$.addresses[0].pincode").value("560011"))
                .andExpect(jsonPath("$.addresses[0].receiverPhone").value("+919845012345"))
                .andExpect(jsonPath("$.addresses[0].selected").value(true));

        when(store.count(CUSTOMER)).thenReturn(0);
        when(store.find(eq(CUSTOMER), any())).thenReturn(Optional.of(home(true)));
        mvc.perform(post("/api/v1/customer/addresses")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sheet(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.city").value("Bengaluru"))
                .andExpect(jsonPath("$.address.selected").value(true));
        verify(store).clearSelected(CUSTOMER);
        verify(store).insert(any(), eq(CUSTOMER), any(), eq(NOW));
    }

    @Test
    void aBadPincodeAndAFullBookAreRejectedBeforeInsert() throws Exception {
        mvc.perform(post("/api/v1/customer/addresses")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sheet(false).replace("560011", "400001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("We deliver across Bengaluru (pincodes starting 560)"));

        when(store.count(CUSTOMER)).thenReturn(10);
        mvc.perform(post("/api/v1/customer/addresses")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sheet(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can save up to 10 addresses"));
        verify(store, never()).insert(any(), any(), any(), any());
    }

    @Test
    void updateAndSelectStayOnTheCustomerAccount() throws Exception {
        mvc.perform(patch("/api/v1/customer/addresses/" + ADDRESS)
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"line1\":\"Flat 1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Address was not found"));

        when(store.find(CUSTOMER, ADDRESS)).thenReturn(Optional.of(home(false)));
        mvc.perform(post("/api/v1/customer/addresses/" + ADDRESS + "/select").principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.id").value(ADDRESS.toString()));
        verify(store).clearSelected(CUSTOMER);
        verify(store).markSelected(CUSTOMER, ADDRESS, NOW);
    }

    private static String sheet(boolean selected) {
        return """
                {"label":"home","line1":"Flat 302, Sri Sai Residency","line2":"7th Cross, Jayanagar 4th Block",
                "city":"Bengaluru","pincode":"560011","receiverName":"Priya Sharma","receiverPhone":"9845012345",
                "selected":%s}
                """
                .formatted(selected);
    }

    private static AddressStore.AddressRow home(boolean selected) {
        return new AddressStore.AddressRow(
                ADDRESS,
                "home",
                "Flat 302, Sri Sai Residency",
                "7th Cross, Jayanagar 4th Block",
                "Near Cool Joint",
                "Bengaluru",
                "560011",
                12.93,
                77.58,
                "Priya Sharma",
                "+919845012345",
                true,
                false,
                false,
                false,
                "Ring twice",
                selected,
                NOW,
                NOW);
    }

    private static AccountAuthentication customer() {
        return account(AccountType.CUSTOMER, false);
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
