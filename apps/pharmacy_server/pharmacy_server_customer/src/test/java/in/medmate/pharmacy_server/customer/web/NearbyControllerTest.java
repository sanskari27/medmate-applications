package in.medmate.pharmacy_server.customer.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.customer.NearbyRules;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.identity.pharmacy.PharmacyRules;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NearbyControllerTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID NEAR = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID FAR = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID SHUT = UUID.fromString("00000000-0000-4000-8000-0000000000b3");

    private final NearbyStore store = Mockito.mock(NearbyStore.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NearbyController(new NearbyService(
                    store,
                    Clock.fixed(
                            LocalDateTime.of(2026, 11, 23, 16, 0)
                                    .atZone(ZoneId.of("Asia/Kolkata"))
                                    .toInstant(),
                            ZoneOffset.UTC))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void guestsAndPharmacyAccountsCannotSearch() throws Exception {
        mvc.perform(get("/api/v1/customer/pharmacies/nearby")
                        .param("latitude", "12.97")
                        .param("longitude", "77.59"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in is required"));
        mvc.perform(get("/api/v1/customer/pharmacies/nearby")
                        .param("latitude", "12.97")
                        .param("longitude", "77.59")
                        .principal(account(AccountType.PHARMACY, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
        mvc.perform(get("/api/v1/customer/pharmacies/nearby")
                        .principal(customer())
                        .param("longitude", "77.59"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Latitude is required"));
        verify(store, never()).nearby(anyDouble(), anyDouble(), anyInt(), anyDouble());
    }

    @Test
    void aCustomerReceivesNearestPharmaciesFirst() throws Exception {
        when(store.nearby(12.9716, 77.5946, 20, NearbyRules.MAX_DISTANCE_KM))
                .thenReturn(List.of(
                        row(
                                NEAR,
                                "Care Pharmacy",
                                0.4,
                                List.of(
                                        new PharmacyRules.OpeningHour("monday", false, "09:00", "13:00"),
                                        new PharmacyRules.OpeningHour("monday", false, "16:00", "21:00"))),
                        row(FAR, "Sri Sai Medicals", 3.2, List.of()),
                        row(
                                SHUT,
                                "Morning Medicals",
                                4.1,
                                List.of(new PharmacyRules.OpeningHour("monday", false, "09:00", "12:00")))));
        mvc.perform(get("/api/v1/customer/pharmacies/nearby")
                        .principal(customer())
                        .param("latitude", "12.9716")
                        .param("longitude", "77.5946"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pharmacies[0].pharmacyId").value(NEAR.toString()))
                .andExpect(jsonPath("$.pharmacies[0].name").value("Care Pharmacy"))
                .andExpect(jsonPath("$.pharmacies[0].distanceKm").value(0.4))
                .andExpect(jsonPath("$.pharmacies[0].acceptOnlineOrders").value(true))
                .andExpect(jsonPath("$.pharmacies[0].licenceNumber").value("KA-BLR-20B-12345"))
                .andExpect(jsonPath("$.pharmacies[0].pharmacistName").value("Asha Rao"))
                .andExpect(jsonPath("$.pharmacies[0].openingHours[0].day").value("monday"))
                .andExpect(jsonPath("$.pharmacies[0].openingHours[0].closed").value(false))
                .andExpect(jsonPath("$.pharmacies[0].openingHours[0].opensAt").value("09:00"))
                .andExpect(jsonPath("$.pharmacies[0].openingHours[0].closesAt").value("13:00"))
                .andExpect(jsonPath("$.pharmacies[0].closed").value(false))
                .andExpect(jsonPath("$.pharmacies[0].closesOn").value("21:00"))
                .andExpect(jsonPath("$.pharmacies[1].openingHours").isEmpty())
                .andExpect(jsonPath("$.pharmacies[1].closed").value(true))
                .andExpect(jsonPath("$.pharmacies[1].closesOn").value(nullValue()))
                .andExpect(jsonPath("$.pharmacies[1].name").value("Sri Sai Medicals"))
                .andExpect(jsonPath("$.pharmacies[1].distanceKm").value(3.2))
                .andExpect(jsonPath("$.pharmacies[2].name").value("Morning Medicals"))
                .andExpect(jsonPath("$.pharmacies[2].closed").value(true))
                .andExpect(jsonPath("$.pharmacies[2].closesOn").value(nullValue()));

        when(store.nearby(12.9716, 77.5946, 50, NearbyRules.MAX_DISTANCE_KM)).thenReturn(List.of());
        mvc.perform(get("/api/v1/customer/pharmacies/nearby")
                        .principal(customer())
                        .param("latitude", "12.9716")
                        .param("longitude", "77.5946")
                        .param("limit", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pharmacies").isEmpty());
        verify(store).nearby(12.9716, 77.5946, 50, NearbyRules.MAX_DISTANCE_KM);
    }

    private static NearbyStore.NearbyRow row(
            UUID id, String name, double distanceKm, List<PharmacyRules.OpeningHour> openingHours) {
        return new NearbyStore.NearbyRow(
                id,
                name,
                "12 Market Road",
                "Bengaluru",
                "Karnataka",
                "560001",
                12.9716,
                77.5946,
                distanceKm,
                true,
                "KA-BLR-20B-12345",
                "Asha Rao",
                openingHours);
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
