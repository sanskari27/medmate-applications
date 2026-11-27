package in.medmate.pharmacy_server.customer.web;

import java.util.List;
import java.util.UUID;

public final class WalletViews {

    private WalletViews() {}

    public record WalletResponse(int balance, String referralCode, String referrerCode) {}

    public record Entry(UUID id, int amount, String kind, UUID orderId, String createdAt) {}

    public record EntriesResponse(List<Entry> entries) {}

    public record QuoteResponse(int balance, int applied, String due) {}
}
