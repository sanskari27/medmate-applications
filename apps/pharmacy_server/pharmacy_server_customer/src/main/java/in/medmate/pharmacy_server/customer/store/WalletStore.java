package in.medmate.pharmacy_server.customer.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "prod", "identity-it"})
public class WalletStore {

    private final JdbcClient jdbc;

    public WalletStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record WalletRow(
            UUID customerId,
            int balance,
            String referralCode,
            UUID referredBy,
            String referrerCode,
            Instant createdAt) {}

    public record EntryRow(UUID id, int amount, String kind, UUID orderId, Instant createdAt) {}

    public void lockLedger() {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked")
                .param("key", 480948510L)
                .query(Integer.class)
                .single();
    }

    public Optional<WalletRow> find(UUID customerId) {
        return jdbc.sql(select() + " WHERE w.customer_id = :id")
                .param("id", customerId)
                .query(WalletStore::wallet)
                .optional();
    }

    public Optional<WalletRow> findByCode(String code) {
        return jdbc.sql(select() + " WHERE w.referral_code = :code")
                .param("code", code)
                .query(WalletStore::wallet)
                .optional();
    }

    public boolean insertWallet(UUID customerId, String referralCode, Instant createdAt) {
        int updated = jdbc.sql(
                        """
                        INSERT INTO customer_wallet (customer_id, balance, referral_code, created_at)
                        VALUES (:id, 0, :code, :createdAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", customerId)
                .param("code", referralCode)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
        return updated == 1;
    }

    public boolean setReferredBy(UUID customerId, UUID referrerId) {
        int updated = jdbc.sql(
                        """
                        UPDATE customer_wallet
                        SET referred_by = :referrer
                        WHERE customer_id = :id AND referred_by IS NULL
                        """)
                .param("id", customerId)
                .param("referrer", referrerId)
                .update();
        return updated == 1;
    }

    public boolean hasOrderEntry(UUID customerId) {
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM wallet_entry WHERE customer_id = :id AND kind = 'order'
                        )
                        """)
                .param("id", customerId)
                .query(Boolean.class)
                .single();
    }

    public boolean referralPaid(UUID referredCustomerId) {
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM wallet_entry
                          WHERE kind = 'referral' AND related_customer_id = :friend
                        )
                        """)
                .param("friend", referredCustomerId)
                .query(Boolean.class)
                .single();
    }

    public Optional<EntryRow> payment(UUID customerId, UUID orderId) {
        return jdbc.sql(
                        """
                        SELECT id, amount, kind, order_id, created_at
                        FROM wallet_entry
                        WHERE customer_id = :customer AND order_id = :orderId AND kind = 'payment'
                        """)
                .param("customer", customerId)
                .param("orderId", orderId)
                .query(WalletStore::entry)
                .optional();
    }

    public List<EntryRow> list(UUID customerId) {
        return jdbc.sql(
                        """
                        SELECT id, amount, kind, order_id, created_at
                        FROM wallet_entry
                        WHERE customer_id = :customer
                        ORDER BY created_at DESC, id DESC
                        """)
                .param("customer", customerId)
                .query(WalletStore::entry)
                .list();
    }

    public boolean insertEntry(
            UUID id,
            UUID customerId,
            int amount,
            String kind,
            UUID orderId,
            UUID relatedCustomerId,
            Instant createdAt) {
        int updated = jdbc.sql(
                        """
                        INSERT INTO wallet_entry (
                          id, customer_id, amount, kind, order_id, related_customer_id, created_at)
                        VALUES (:id, :customer, :amount, :kind, :orderId, :related, :createdAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", id)
                .param("customer", customerId)
                .param("amount", amount)
                .param("kind", kind)
                .param("orderId", orderId, Types.OTHER)
                .param("related", relatedCustomerId, Types.OTHER)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
        return updated == 1;
    }

    public void addBalance(UUID customerId, int delta) {
        int updated = jdbc.sql(
                        """
                        UPDATE customer_wallet
                        SET balance = balance + :delta
                        WHERE customer_id = :id AND balance + :delta >= 0
                        """)
                .param("id", customerId)
                .param("delta", delta)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Wallet balance was not updated");
        }
    }

    private static String select() {
        return """
                SELECT w.customer_id, w.balance, w.referral_code, w.referred_by,
                       referrer.referral_code AS referrer_code, w.created_at
                FROM customer_wallet w
                LEFT JOIN customer_wallet referrer ON referrer.customer_id = w.referred_by
                """;
    }

    private static WalletRow wallet(ResultSet rs, int ignored) throws SQLException {
        return new WalletRow(
                rs.getObject("customer_id", UUID.class),
                rs.getInt("balance"),
                rs.getString("referral_code"),
                rs.getObject("referred_by", UUID.class),
                rs.getString("referrer_code"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static EntryRow entry(ResultSet rs, int ignored) throws SQLException {
        return new EntryRow(
                rs.getObject("id", UUID.class),
                rs.getInt("amount"),
                rs.getString("kind"),
                rs.getObject("order_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
