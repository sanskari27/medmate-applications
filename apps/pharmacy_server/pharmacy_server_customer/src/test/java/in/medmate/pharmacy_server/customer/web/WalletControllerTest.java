package in.medmate.pharmacy_server.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.customer.store.WalletStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WalletControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-4000-8000-0000000000bb");
    private static final UUID ORDER = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID ENTRY = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    private final WalletStore store = Mockito.mock(WalletStore.class);
    private final WalletService wallets = new WalletService(store, Clock.fixed(NOW, ZoneOffset.UTC));

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WalletController(wallets))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void guestsAndPharmacyAccountsCannotReadAWallet() throws Exception {
        mvc.perform(get("/api/v1/customer/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in is required"));
        mvc.perform(get("/api/v1/customer/wallet").principal(account(AccountType.PHARMACY, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action is not allowed."));
        mvc.perform(get("/api/v1/customer/wallet/entries").principal(account(AccountType.CUSTOMER, true)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/wallet/quote")
                        .principal(account(AccountType.PHARMACY, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payable\":\"10.00\"}"))
                .andExpect(status().isForbidden());
        verify(store, never()).find(any());
    }

    @Test
    void aCustomerOpensAnEmptyWalletAndReadsTheLedger() throws Exception {
        when(store.find(CUSTOMER)).thenReturn(Optional.empty(), Optional.of(wallet(0, "ABCD2345", null, null)));
        when(store.insertWallet(eq(CUSTOMER), any(), eq(NOW))).thenReturn(true);
        mvc.perform(get("/api/v1/customer/wallet").principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.referralCode").value("ABCD2345"))
                .andExpect(jsonPath("$.referrerCode").value(org.hamcrest.Matchers.nullValue()));

        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(15, "ABCD2345", null, null)));
        when(store.list(CUSTOMER)).thenReturn(List.of(new WalletStore.EntryRow(ENTRY, 15, "order", ORDER, NOW)));
        mvc.perform(get("/api/v1/customer/wallet/entries").principal(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].amount").value(15))
                .andExpect(jsonPath("$.entries[0].kind").value("order"))
                .andExpect(jsonPath("$.entries[0].orderId").value(ORDER.toString()))
                .andExpect(jsonPath("$.entries[0].createdAt").value("2026-11-26T03:00:00Z"));
    }

    @Test
    void aCustomerSavesAFriendsCodeOnceBeforeTheFirstOrder() throws Exception {
        when(store.find(CUSTOMER))
                .thenReturn(
                        Optional.of(wallet(0, "ABCD2345", null, null)),
                        Optional.of(wallet(0, "ABCD2345", null, null)),
                        Optional.of(wallet(0, "ABCD2345", FRIEND, "WXYZ6789")));
        when(store.findByCode("WXYZ6789")).thenReturn(Optional.of(wallet(FRIEND, 40, "WXYZ6789", null, null)));
        when(store.hasOrderEntry(CUSTOMER)).thenReturn(false);
        when(store.setReferredBy(CUSTOMER, FRIEND)).thenReturn(true);
        mvc.perform(post("/api/v1/customer/wallet/referral")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" wxyz6789 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referrerCode").value("WXYZ6789"));
        verify(store).lockLedger();
        verify(store).setReferredBy(CUSTOMER, FRIEND);

        when(store.findByCode("ABCD2345")).thenReturn(Optional.of(wallet(0, "ABCD2345", null, null)));
        mvc.perform(post("/api/v1/customer/wallet/referral")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD2345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot use your own referral code"));

        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(0, "ABCD2345", FRIEND, "WXYZ6789")));
        when(store.findByCode("WXYZ6789")).thenReturn(Optional.of(wallet(FRIEND, 40, "WXYZ6789", null, null)));
        mvc.perform(post("/api/v1/customer/wallet/referral")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WXYZ6789\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A referral code is already saved"));

        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(0, "ABCD2345", null, null)));
        when(store.hasOrderEntry(CUSTOMER)).thenReturn(true);
        mvc.perform(post("/api/v1/customer/wallet/referral")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WXYZ6789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Referral codes can only be used before your first order"));

        when(store.findByCode("MNPRSTVW")).thenReturn(Optional.empty());
        when(store.hasOrderEntry(CUSTOMER)).thenReturn(false);
        mvc.perform(post("/api/v1/customer/wallet/referral")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MNPRSTVW\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Referral code was not found"));
    }

    @Test
    void aQuoteShowsHowManyCreditsAPaymentWouldUse() throws Exception {
        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(300, "ABCD2345", null, null)));
        mvc.perform(post("/api/v1/customer/wallet/quote")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payable\":\"249.50\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300))
                .andExpect(jsonPath("$.applied").value(249))
                .andExpect(jsonPath("$.due").value("0.50"));
        verify(store, never()).insertEntry(any(), any(), anyInt(), any(), any(), any(), any());
        verify(store, never()).addBalance(any(), anyInt());

        mvc.perform(post("/api/v1/customer/wallet/quote")
                        .principal(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payable\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Payable is invalid"));
    }

    @Test
    void placingAnOrderCreditsTheBuyerAndTheReferrerOnce() {
        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(0, "ABCD2345", FRIEND, "WXYZ6789")));
        when(store.find(FRIEND)).thenReturn(Optional.of(wallet(FRIEND, 40, "WXYZ6789", null, null)));
        when(store.insertEntry(any(), any(), anyInt(), any(), nullable(UUID.class), nullable(UUID.class), eq(NOW)))
                .thenReturn(true);
        when(store.referralPaid(CUSTOMER)).thenReturn(false);

        WalletService.OrderCredit credit = wallets.onOrderPlaced(CUSTOMER, ORDER);

        assertThat(credit.orderCredited()).isTrue();
        assertThat(credit.referralCredited()).isTrue();
        verify(store).addBalance(CUSTOMER, 15);
        verify(store).addBalance(FRIEND, 100);
        verify(store).insertEntry(any(), eq(CUSTOMER), eq(15), eq("order"), eq(ORDER), isNull(), eq(NOW));
        verify(store).insertEntry(any(), eq(FRIEND), eq(100), eq("referral"), isNull(), eq(CUSTOMER), eq(NOW));

        when(store.insertEntry(any(), any(), anyInt(), any(), nullable(UUID.class), nullable(UUID.class), eq(NOW)))
                .thenReturn(false);
        when(store.referralPaid(CUSTOMER)).thenReturn(true);
        WalletService.OrderCredit again = wallets.onOrderPlaced(CUSTOMER, ORDER);
        assertThat(again.orderCredited()).isFalse();
        assertThat(again.referralCredited()).isFalse();
    }

    @Test
    void aPaymentDebitsTheBalanceOnceAndARepeatReturnsTheSameDebit() {
        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(100, "ABCD2345", null, null)));
        when(store.payment(CUSTOMER, ORDER)).thenReturn(Optional.empty());
        when(store.insertEntry(any(), eq(CUSTOMER), eq(-100), eq("payment"), eq(ORDER), isNull(), eq(NOW)))
                .thenReturn(true);

        WalletService.Payment payment = wallets.applyToPayment(CUSTOMER, ORDER, new BigDecimal("249.50"));

        assertThat(payment.applied()).isEqualTo(100);
        assertThat(payment.due()).isEqualByComparingTo("149.50");
        verify(store).addBalance(CUSTOMER, -100);

        when(store.payment(CUSTOMER, ORDER))
                .thenReturn(Optional.of(new WalletStore.EntryRow(ENTRY, -100, "payment", ORDER, NOW)));
        WalletService.Payment repeat = wallets.applyToPayment(CUSTOMER, ORDER, new BigDecimal("249.50"));
        assertThat(repeat.applied()).isEqualTo(100);
        assertThat(repeat.due()).isEqualByComparingTo("149.50");
        verify(store).addBalance(CUSTOMER, -100);
    }

    @Test
    void aPaymentOfOnlyPaiseDoesNotDebit() {
        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(10, "ABCD2345", null, null)));
        when(store.payment(CUSTOMER, ORDER)).thenReturn(Optional.empty());

        WalletService.Payment payment = wallets.applyToPayment(CUSTOMER, ORDER, new BigDecimal("0.40"));

        assertThat(payment.applied()).isZero();
        assertThat(payment.due()).isEqualByComparingTo("0.40");
        verify(store, never())
                .insertEntry(any(), any(), anyInt(), any(), nullable(UUID.class), nullable(UUID.class), any());
        verify(store, never()).addBalance(any(), anyInt());
    }

    @Test
    void placingAnOrderWithoutAReferrerCreditsOnlyTheBuyer() {
        when(store.find(CUSTOMER)).thenReturn(Optional.of(wallet(0, "ABCD2345", null, null)));
        when(store.insertEntry(any(), any(), anyInt(), any(), nullable(UUID.class), nullable(UUID.class), eq(NOW)))
                .thenReturn(true);

        WalletService.OrderCredit credit = wallets.onOrderPlaced(CUSTOMER, ORDER);

        assertThat(credit.orderCredited()).isTrue();
        assertThat(credit.referralCredited()).isFalse();
        verify(store).addBalance(CUSTOMER, 15);
        verify(store, never()).addBalance(eq(FRIEND), anyInt());
        verify(store, never()).referralPaid(any());
    }

    private static WalletStore.WalletRow wallet(int balance, String code, UUID referredBy, String referrerCode) {
        return wallet(CUSTOMER, balance, code, referredBy, referrerCode);
    }

    private static WalletStore.WalletRow wallet(
            UUID customerId, int balance, String code, UUID referredBy, String referrerCode) {
        return new WalletStore.WalletRow(customerId, balance, code, referredBy, referrerCode, NOW);
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
