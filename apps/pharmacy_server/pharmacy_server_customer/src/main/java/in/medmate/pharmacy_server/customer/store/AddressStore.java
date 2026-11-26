package in.medmate.pharmacy_server.customer.store;

import in.medmate.pharmacy_server.customer.AddressRules;
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
public class AddressStore {

    private final JdbcClient jdbc;

    public AddressStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record AddressRow(
            UUID id,
            String label,
            String line1,
            String line2,
            String landmark,
            String city,
            String pincode,
            Double latitude,
            Double longitude,
            String receiverName,
            String receiverPhone,
            boolean callOnArrival,
            boolean leaveWithSecurity,
            boolean dontRingBell,
            boolean liftNotWorking,
            String riderNote,
            boolean selected,
            Instant createdAt,
            Instant updatedAt) {}

    public int count(UUID accountId) {
        Long total = jdbc.sql("SELECT COUNT(*) FROM customer_delivery_address WHERE account_id = :account")
                .param("account", accountId)
                .query(Long.class)
                .single();
        return total.intValue();
    }

    public List<AddressRow> list(UUID accountId) {
        return jdbc.sql(select() + " WHERE account_id = :account ORDER BY updated_at DESC, id DESC")
                .param("account", accountId)
                .query(AddressStore::row)
                .list();
    }

    public Optional<AddressRow> find(UUID accountId, UUID id) {
        return jdbc.sql(select() + " WHERE account_id = :account AND id = :id")
                .param("account", accountId)
                .param("id", id)
                .query(AddressStore::row)
                .optional();
    }

    public void insert(UUID id, UUID accountId, AddressRules.Values values, Instant now) {
        bind(
                        jdbc.sql(
                                """
                        INSERT INTO customer_delivery_address (
                          id, account_id, label, line1, line2, landmark, city, pincode, latitude, longitude,
                          receiver_name, receiver_phone, call_on_arrival, leave_with_security, dont_ring_bell,
                          lift_not_working, rider_note, selected, created_at, updated_at)
                        VALUES (
                          :id, :account, :label, :line1, :line2, :landmark, :city, :pincode, :latitude, :longitude,
                          :receiverName, :receiverPhone, :callOnArrival, :leaveWithSecurity, :dontRingBell,
                          :liftNotWorking, :riderNote, :selected, :now, :now)
                        """),
                        id,
                        accountId,
                        values,
                        now)
                .update();
    }

    public void update(UUID id, UUID accountId, AddressRules.Values values, Instant now) {
        bind(
                        jdbc.sql(
                                """
                        UPDATE customer_delivery_address SET
                          label = :label, line1 = :line1, line2 = :line2, landmark = :landmark, city = :city,
                          pincode = :pincode, latitude = :latitude, longitude = :longitude,
                          receiver_name = :receiverName, receiver_phone = :receiverPhone,
                          call_on_arrival = :callOnArrival, leave_with_security = :leaveWithSecurity,
                          dont_ring_bell = :dontRingBell, lift_not_working = :liftNotWorking,
                          rider_note = :riderNote, selected = :selected, updated_at = :now
                        WHERE account_id = :account AND id = :id
                        """),
                        id,
                        accountId,
                        values,
                        now)
                .update();
    }

    public void clearSelected(UUID accountId) {
        jdbc.sql(
                        """
                        UPDATE customer_delivery_address
                        SET selected = FALSE
                        WHERE account_id = :account AND selected
                        """)
                .param("account", accountId)
                .update();
    }

    public void markSelected(UUID accountId, UUID id, Instant now) {
        jdbc.sql(
                        """
                        UPDATE customer_delivery_address
                        SET selected = TRUE, updated_at = :now
                        WHERE account_id = :account AND id = :id
                        """)
                .param("account", accountId)
                .param("id", id)
                .param("now", Timestamp.from(now))
                .update();
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec spec, UUID id, UUID accountId, AddressRules.Values values, Instant now) {
        return spec.param("id", id)
                .param("account", accountId)
                .param("label", values.label(), Types.VARCHAR)
                .param("line1", values.line1(), Types.VARCHAR)
                .param("line2", values.line2(), Types.VARCHAR)
                .param("landmark", values.landmark(), Types.VARCHAR)
                .param("city", values.city(), Types.VARCHAR)
                .param("pincode", values.pincode(), Types.VARCHAR)
                .param("latitude", values.latitude(), Types.DOUBLE)
                .param("longitude", values.longitude(), Types.DOUBLE)
                .param("receiverName", values.receiverName(), Types.VARCHAR)
                .param("receiverPhone", values.receiverPhone(), Types.VARCHAR)
                .param("callOnArrival", values.callOnArrival())
                .param("leaveWithSecurity", values.leaveWithSecurity())
                .param("dontRingBell", values.dontRingBell())
                .param("liftNotWorking", values.liftNotWorking())
                .param("riderNote", values.riderNote(), Types.VARCHAR)
                .param("selected", values.selected())
                .param("now", Timestamp.from(now));
    }

    private static String select() {
        return """
                SELECT id, label, line1, line2, landmark, city, pincode, latitude, longitude,
                       receiver_name, receiver_phone, call_on_arrival, leave_with_security, dont_ring_bell,
                       lift_not_working, rider_note, selected, created_at, updated_at
                FROM customer_delivery_address
                """;
    }

    private static AddressRow row(ResultSet rs, int ignored) throws SQLException {
        return new AddressRow(
                rs.getObject("id", UUID.class),
                rs.getString("label"),
                rs.getString("line1"),
                rs.getString("line2"),
                rs.getString("landmark"),
                rs.getString("city"),
                rs.getString("pincode"),
                coordinate(rs, "latitude"),
                coordinate(rs, "longitude"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getBoolean("call_on_arrival"),
                rs.getBoolean("leave_with_security"),
                rs.getBoolean("dont_ring_bell"),
                rs.getBoolean("lift_not_working"),
                rs.getString("rider_note"),
                rs.getBoolean("selected"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Double coordinate(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
