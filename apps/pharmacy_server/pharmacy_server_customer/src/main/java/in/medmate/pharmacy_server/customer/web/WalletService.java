package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.WalletRules;
import in.medmate.pharmacy_server.customer.store.WalletStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
public class WalletService {

    private static final int CODE_ATTEMPTS = 8;

    private final WalletStore wallets;
    private final Clock clock;

    public WalletService(WalletStore wallets, Clock clock) {
        this.wallets = wallets;
        this.clock = clock;
    }

    public record OrderCredit(boolean orderCredited, boolean referralCredited) {}

    public record Payment(int applied, BigDecimal due) {}

    @Transactional
    public WalletViews.WalletResponse read(AccountPrincipal actor) {
        requireCustomer(actor);
        return view(ensure(actor.id()));
    }

    @Transactional
    public WalletViews.EntriesResponse entries(AccountPrincipal actor) {
        requireCustomer(actor);
        ensure(actor.id());
        List<WalletViews.Entry> rows =
                wallets.list(actor.id()).stream().map(WalletService::entry).toList();
        return new WalletViews.EntriesResponse(rows);
    }

    @Transactional
    public WalletViews.WalletResponse link(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        WalletRules.PreparedCode prepared = WalletRules.prepareCode(body);
        if (prepared.problem() != null) {
            throw bad(prepared.problem());
        }
        ensure(actor.id());
        wallets.lockLedger();
        WalletStore.WalletRow self = wallets.find(actor.id()).orElseThrow();
        WalletStore.WalletRow owner = wallets.findByCode(prepared.code()).orElse(null);
        String problem = WalletRules.linkProblem(
                owner == null,
                owner != null && owner.customerId().equals(self.customerId()),
                self.referredBy() != null,
                wallets.hasOrderEntry(self.customerId()),
                owner != null && self.customerId().equals(owner.referredBy()));
        if (problem != null || owner == null) {
            throw linkFailure(problem == null ? "Referral code was not found" : problem);
        }
        if (!wallets.setReferredBy(self.customerId(), owner.customerId())) {
            throw linkFailure("A referral code is already saved");
        }
        return view(wallets.find(self.customerId()).orElseThrow());
    }

    @Transactional
    public WalletViews.QuoteResponse quote(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        BigDecimal payable = WalletRules.payable(body);
        String problem = WalletRules.payableProblem(payable);
        if (problem != null) {
            throw bad(problem);
        }
        WalletStore.WalletRow wallet = ensure(actor.id());
        WalletRules.Settlement settlement = WalletRules.quote(wallet.balance(), payable);
        return new WalletViews.QuoteResponse(
                settlement.balance(), settlement.applied(), WalletRules.money(settlement.due()));
    }

    @Transactional
    public OrderCredit onOrderPlaced(UUID customerId, UUID orderId) {
        if (customerId == null || orderId == null) {
            throw new IllegalArgumentException("Order credit needs a customer and an order");
        }
        ensure(customerId);
        wallets.lockLedger();
        Instant now = clock.instant();
        boolean orderCredited = wallets.insertEntry(
                UUID.randomUUID(), customerId, WalletRules.ORDER_CREDITS, WalletRules.ORDER, orderId, null, now);
        if (orderCredited) {
            wallets.addBalance(customerId, WalletRules.ORDER_CREDITS);
        }
        WalletStore.WalletRow buyer = wallets.find(customerId).orElseThrow();
        if (buyer.referredBy() == null || wallets.referralPaid(customerId)) {
            return new OrderCredit(orderCredited, false);
        }
        ensure(buyer.referredBy());
        boolean referralCredited = wallets.insertEntry(
                UUID.randomUUID(),
                buyer.referredBy(),
                WalletRules.REFERRAL_CREDITS,
                WalletRules.REFERRAL,
                null,
                customerId,
                now);
        if (referralCredited) {
            wallets.addBalance(buyer.referredBy(), WalletRules.REFERRAL_CREDITS);
        }
        return new OrderCredit(orderCredited, referralCredited);
    }

    @Transactional
    public Payment applyToPayment(UUID customerId, UUID orderId, BigDecimal payable) {
        if (customerId == null || orderId == null) {
            throw new IllegalArgumentException("Wallet payment needs a customer and an order");
        }
        String problem = WalletRules.payableProblem(payable);
        if (problem != null) {
            throw bad(problem);
        }
        ensure(customerId);
        wallets.lockLedger();
        WalletStore.EntryRow existing = wallets.payment(customerId, orderId).orElse(null);
        if (existing != null) {
            return new Payment(-existing.amount(), WalletRules.due(payable, -existing.amount()));
        }
        WalletStore.WalletRow wallet = wallets.find(customerId).orElseThrow();
        WalletRules.Settlement settlement = WalletRules.quote(wallet.balance(), payable);
        if (settlement.applied() == 0) {
            return new Payment(0, settlement.due());
        }
        boolean inserted = wallets.insertEntry(
                UUID.randomUUID(),
                customerId,
                -settlement.applied(),
                WalletRules.PAYMENT,
                orderId,
                null,
                clock.instant());
        if (!inserted) {
            WalletStore.EntryRow again = wallets.payment(customerId, orderId).orElseThrow();
            return new Payment(-again.amount(), WalletRules.due(payable, -again.amount()));
        }
        wallets.addBalance(customerId, -settlement.applied());
        return new Payment(settlement.applied(), settlement.due());
    }

    private WalletStore.WalletRow ensure(UUID customerId) {
        WalletStore.WalletRow existing = wallets.find(customerId).orElse(null);
        if (existing != null) {
            return existing;
        }
        Instant now = clock.instant();
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = WalletRules.code(ThreadLocalRandom.current());
            if (wallets.insertWallet(customerId, code, now)) {
                return wallets.find(customerId).orElseThrow();
            }
            existing = wallets.find(customerId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        throw new IllegalStateException("Wallet could not be opened");
    }

    private static WalletViews.WalletResponse view(WalletStore.WalletRow row) {
        return new WalletViews.WalletResponse(row.balance(), row.referralCode(), row.referrerCode());
    }

    private static WalletViews.Entry entry(WalletStore.EntryRow row) {
        return new WalletViews.Entry(
                row.id(),
                row.amount(),
                row.kind(),
                row.orderId(),
                DateTimeFormatter.ISO_INSTANT.format(row.createdAt()));
    }

    private void requireCustomer(AccountPrincipal actor) {
        if (actor == null) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Sign in is required");
        }
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
    }

    private static ApiProblem bad(String detail) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private static ApiProblem linkFailure(String detail) {
        if ("Referral code was not found".equals(detail)) {
            return new ApiProblem(HttpStatus.NOT_FOUND, "Not found", detail);
        }
        if ("A referral code is already saved".equals(detail)) {
            return new ApiProblem(HttpStatus.CONFLICT, "Conflict", detail);
        }
        return bad(detail);
    }
}
