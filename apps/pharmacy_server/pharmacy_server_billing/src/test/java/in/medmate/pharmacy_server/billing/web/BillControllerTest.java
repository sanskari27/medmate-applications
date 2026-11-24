package in.medmate.pharmacy_server.billing.web;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.billing.store.BillStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.identity.pharmacy.InvoiceNumbers;
import in.medmate.pharmacy_server.offers.ActiveOffers;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import in.medmate.pharmacy_server.stock.CounterStock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BillControllerTest {

    private static final UUID PHARMACY = UUID.fromString("00000000-0000-4000-8000-000000000010");

    private final BillStore store = Mockito.mock(BillStore.class);
    private final CounterStock stock = Mockito.mock(CounterStock.class);
    private final ActiveOffers offers = Mockito.mock(ActiveOffers.class);
    private final InvoiceNumbers numbers = Mockito.mock(InvoiceNumbers.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new BillController(new BillService(store, stock, offers, numbers)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void readDoesNotPost() throws Exception {
        mvc.perform(post("/pharmacy/bills")
                        .principal(staff("billing:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Bills cannot be created"));
        verify(stock, never()).lockPharmacy(PHARMACY);
        verify(store, never()).allocate(PHARMACY);
    }

    @Test
    void createReachesThePharmacy() throws Exception {
        when(stock.lockPharmacy(PHARMACY)).thenReturn(false);
        mvc.perform(post("/pharmacy/bills")
                        .principal(staff("billing:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pharmacy was not found"));
        verify(store, never()).allocate(PHARMACY);
    }

    private static String body() {
        return """
                {"patientName":"Ada","patientPhone":"9886012345","payMode":"cash",
                 "lines":[{"productId":"med-a","batchNo":"B1","qty":1}]}
                """;
    }

    private static AccountAuthentication staff(String... keys) {
        AccountPrincipal principal = new AccountPrincipal(
                UUID.randomUUID(),
                "919800000000",
                AccountType.PHARMACY,
                "Ada",
                false,
                PHARMACY,
                PharmacyRole.STAFF,
                VerificationStatus.APPROVED,
                null,
                null,
                Set.of(),
                Set.of(keys),
                true,
                null);
        return new AccountAuthentication(principal);
    }
}
