package in.medmate.pharmacy_server.cart.store;

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
public class CartStore {

    private final JdbcClient jdbc;

    public CartStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record CartRow(UUID accountId, String couponCode) {}

    public record LineRow(String productId, int qty) {}

    public Optional<CartRow> find(UUID accountId) {
        return jdbc.sql("SELECT account_id, coupon_code FROM customer_cart WHERE account_id = :account")
                .param("account", accountId)
                .query(CartStore::cart)
                .optional();
    }

    public void ensure(UUID accountId, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO customer_cart (account_id, updated_at)
                        VALUES (:account, :now)
                        ON CONFLICT (account_id) DO NOTHING
                        """)
                .param("account", accountId)
                .param("now", Timestamp.from(now))
                .update();
    }

    public List<LineRow> lines(UUID accountId) {
        return jdbc.sql(
                        """
                        SELECT product_id, qty
                        FROM customer_cart_line
                        WHERE account_id = :account
                        ORDER BY product_id
                        """)
                .param("account", accountId)
                .query((row, number) -> new LineRow(row.getString("product_id"), row.getInt("qty")))
                .list();
    }

    public int lineCount(UUID accountId) {
        return jdbc.sql("SELECT COUNT(*) FROM customer_cart_line WHERE account_id = :account")
                .param("account", accountId)
                .query(Integer.class)
                .single();
    }

    public boolean hasLine(UUID accountId, String productId) {
        return jdbc.sql(
                        """
                        SELECT product_id FROM customer_cart_line
                        WHERE account_id = :account AND product_id = :product
                        """)
                .param("account", accountId)
                .param("product", productId)
                .query(String.class)
                .optional()
                .isPresent();
    }

    public void saveLine(UUID accountId, String productId, int qty, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO customer_cart_line (account_id, product_id, qty)
                        VALUES (:account, :product, :qty)
                        ON CONFLICT (account_id, product_id) DO UPDATE SET qty = EXCLUDED.qty
                        """)
                .param("account", accountId)
                .param("product", productId)
                .param("qty", qty)
                .update();
        touch(accountId, now);
    }

    public void deleteLine(UUID accountId, String productId, Instant now) {
        jdbc.sql("DELETE FROM customer_cart_line WHERE account_id = :account AND product_id = :product")
                .param("account", accountId)
                .param("product", productId)
                .update();
        touch(accountId, now);
    }

    public List<UUID> pharmacies(UUID accountId) {
        return jdbc.sql(
                        """
                        SELECT pharmacy_id FROM customer_cart_pharmacy
                        WHERE account_id = :account
                        ORDER BY pharmacy_id
                        """)
                .param("account", accountId)
                .query(UUID.class)
                .list();
    }

    public void replacePharmacies(UUID accountId, List<UUID> pharmacyIds, Instant now) {
        jdbc.sql("DELETE FROM customer_cart_pharmacy WHERE account_id = :account")
                .param("account", accountId)
                .update();
        for (UUID pharmacyId : pharmacyIds) {
            jdbc.sql(
                            """
                            INSERT INTO customer_cart_pharmacy (account_id, pharmacy_id)
                            VALUES (:account, :pharmacy)
                            """)
                    .param("account", accountId)
                    .param("pharmacy", pharmacyId)
                    .update();
        }
        touch(accountId, now);
    }

    public void coupon(UUID accountId, String code, Instant now) {
        jdbc.sql("UPDATE customer_cart SET coupon_code = :code, updated_at = :now WHERE account_id = :account")
                .param("account", accountId)
                .param("code", code, Types.VARCHAR)
                .param("now", Timestamp.from(now))
                .update();
    }

    public void clearCoupon(UUID accountId, Instant now) {
        coupon(accountId, null, now);
    }

    private void touch(UUID accountId, Instant now) {
        jdbc.sql("UPDATE customer_cart SET updated_at = :now WHERE account_id = :account")
                .param("account", accountId)
                .param("now", Timestamp.from(now))
                .update();
    }

    private static CartRow cart(ResultSet row, int number) throws SQLException {
        return new CartRow(row.getObject("account_id", UUID.class), row.getString("coupon_code"));
    }
}
