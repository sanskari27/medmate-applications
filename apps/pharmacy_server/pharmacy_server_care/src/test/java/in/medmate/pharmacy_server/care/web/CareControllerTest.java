package in.medmate.pharmacy_server.care.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.care.store.CareStore;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CareControllerTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID SELF = UUID.fromString("00000000-0000-4000-8000-0000000000bb");

    private final CareStore store = Mockito.mock(CareStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CareController(new CareService(
                    store,
                    Mockito.mock(CatalogStore.class),
                    Clock.fixed(Instant.parse("2026-11-30T08:00:00Z"), ZoneId.of("Asia/Kolkata")))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void aPharmacyCannotReadTheFamily() throws Exception {
        mvc.perform(get("/api/v1/customer/family").principal(account(AccountType.PHARMACY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
    }

    @Test
    void aCustomerSeesTheAccountHolderOnAnEmptySchedule() throws Exception {
        when(store.self(CUSTOMER)).thenReturn(Optional.empty());
        when(store.members(CUSTOMER)).thenReturn(List.of(self()));
        when(store.medicines(eq(CUSTOMER), isNull())).thenReturn(List.of());
        when(store.times(CUSTOMER)).thenReturn(List.of());
        when(store.doses(eq(CUSTOMER), any(), any(), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/v1/customer/family").principal(account(AccountType.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-11-30"))
                .andExpect(jsonPath("$.members[0].displayName").value("Priya"))
                .andExpect(jsonPath("$.members[0].relation").value("self"));
    }

    @Test
    void aMedicineNeedsAName() throws Exception {
        when(store.self(CUSTOMER)).thenReturn(Optional.of(self()));

        mvc.perform(post("/api/v1/customer/medicines")
                        .principal(account(AccountType.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose who this medicine is for"));
    }

    private static CareStore.MemberRow self() {
        return new CareStore.MemberRow(SELF, "Priya", "self", null, null, "", "", null, true);
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
