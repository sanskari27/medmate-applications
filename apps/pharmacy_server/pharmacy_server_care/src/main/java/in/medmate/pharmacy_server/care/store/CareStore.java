package in.medmate.pharmacy_server.care.store;

import in.medmate.pharmacy_server.care.CareRules;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "prod", "identity-it"})
public class CareStore {

    private final JdbcClient jdbc;

    public CareStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record MemberRow(
            UUID id,
            String displayName,
            String relation,
            Integer ageYears,
            LocalDate birthday,
            String conditions,
            String allergies,
            String mobile,
            boolean consent) {}

    public record MedicineRow(
            UUID id,
            UUID memberId,
            String productId,
            String name,
            BigDecimal doseAmount,
            String doseUnit,
            String food,
            String weekdays,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal stockUnits,
            BigDecimal unitsPerDose,
            boolean reminders,
            String note) {}

    public record TimeRow(UUID medicineId, LocalTime time) {}

    public record DoseRow(UUID id, UUID medicineId, UUID memberId, LocalDate date, LocalTime time, String status) {}

    public record DoseActionRow(UUID id, UUID medicineId, String status, BigDecimal unitsPerDose, LocalDate date) {}

    public record PrescriptionRow(UUID id, String source, LocalDate issuedOn, boolean open) {}

    public record LineRow(
            UUID id, UUID prescriptionId, String productId, String name, String doseLabel, boolean scheduled) {}

    public Optional<MemberRow> self(UUID accountId) {
        return jdbc.sql(memberSelect() + " WHERE account_id = :account AND relation = 'self'")
                .param("account", accountId)
                .query(CareStore::member)
                .optional();
    }

    public List<MemberRow> members(UUID accountId) {
        return jdbc.sql(memberSelect()
                        + " WHERE account_id = :account ORDER BY relation = 'self' DESC, display_name, id")
                .param("account", accountId)
                .query(CareStore::member)
                .list();
    }

    public Optional<MemberRow> member(UUID accountId, UUID id) {
        return jdbc.sql(memberSelect() + " WHERE account_id = :account AND id = :id")
                .param("account", accountId)
                .param("id", id)
                .query(CareStore::member)
                .optional();
    }

    public void insertMember(UUID id, UUID accountId, CareRules.MemberValues values, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO customer_family_member (
                          id, account_id, display_name, relation, age_years, birthday, conditions, allergies,
                          mobile, consent, created_at, updated_at)
                        VALUES (
                          :id, :account, :name, :relation, :age, :birthday, :conditions, :allergies,
                          :mobile, :consent, :now, :now)
                        """)
                .param("id", id)
                .param("account", accountId)
                .param("name", values.displayName(), Types.VARCHAR)
                .param("relation", values.relation(), Types.VARCHAR)
                .param("age", values.ageYears(), Types.INTEGER)
                .param("birthday", values.birthday() == null ? null : Date.valueOf(values.birthday()), Types.DATE)
                .param("conditions", CareRules.joinList(values.conditions()), Types.VARCHAR)
                .param("allergies", CareRules.joinList(values.allergies()), Types.VARCHAR)
                .param("mobile", values.mobile(), Types.VARCHAR)
                .param("consent", values.consent())
                .param("now", Timestamp.from(now))
                .update();
    }

    public void updateMember(UUID id, UUID accountId, CareRules.MemberValues values, Instant now) {
        jdbc.sql(
                        """
                        UPDATE customer_family_member SET
                          display_name = :name, relation = :relation, age_years = :age, birthday = :birthday,
                          conditions = :conditions, allergies = :allergies, mobile = :mobile, consent = :consent,
                          updated_at = :now
                        WHERE account_id = :account AND id = :id
                        """)
                .param("name", values.displayName(), Types.VARCHAR)
                .param("relation", values.relation(), Types.VARCHAR)
                .param("age", values.ageYears(), Types.INTEGER)
                .param("birthday", values.birthday() == null ? null : Date.valueOf(values.birthday()), Types.DATE)
                .param("conditions", CareRules.joinList(values.conditions()), Types.VARCHAR)
                .param("allergies", CareRules.joinList(values.allergies()), Types.VARCHAR)
                .param("mobile", values.mobile(), Types.VARCHAR)
                .param("consent", values.consent())
                .param("now", Timestamp.from(now))
                .param("account", accountId)
                .param("id", id)
                .update();
    }

    public List<MedicineRow> medicines(UUID accountId, UUID memberId) {
        String sql = medicineSelect() + " WHERE account_id = :account";
        if (memberId != null) {
            sql += " AND member_id = :member";
        }
        sql += " ORDER BY name, id";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("account", accountId);
        if (memberId != null) {
            spec = spec.param("member", memberId);
        }
        return spec.query(CareStore::medicine).list();
    }

    public Optional<MedicineRow> medicine(UUID accountId, UUID id) {
        return jdbc.sql(medicineSelect() + " WHERE account_id = :account AND id = :id")
                .param("account", accountId)
                .param("id", id)
                .query(CareStore::medicine)
                .optional();
    }

    public void insertMedicine(
            UUID id, UUID accountId, CareRules.MedicineValues values, UUID prescriptionLineId, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO customer_medicine (
                          id, account_id, member_id, product_id, name, dose_amount, dose_unit, food, weekdays,
                          starts_on, ends_on, stock_units, units_per_dose, reminders, note, prescription_line_id,
                          created_at, updated_at)
                        VALUES (
                          :id, :account, :member, :product, :name, :amount, :unit, :food, :weekdays,
                          :starts, :ends, :stock, :units, :reminders, :note, :line, :now, :now)
                        """)
                .param("id", id)
                .param("account", accountId)
                .param("member", values.memberId())
                .param("product", values.productId(), Types.VARCHAR)
                .param("name", values.name(), Types.VARCHAR)
                .param("amount", values.doseAmount())
                .param("unit", values.doseUnit(), Types.VARCHAR)
                .param("food", values.food(), Types.VARCHAR)
                .param("weekdays", values.weekdays(), Types.VARCHAR)
                .param("starts", Date.valueOf(values.startsOn()))
                .param("ends", values.endsOn() == null ? null : Date.valueOf(values.endsOn()), Types.DATE)
                .param("stock", values.stockUnits())
                .param("units", values.doseAmount())
                .param("reminders", values.reminders())
                .param("note", values.note(), Types.VARCHAR)
                .param("line", prescriptionLineId, Types.OTHER)
                .param("now", Timestamp.from(now))
                .update();
    }

    public void updateMedicine(UUID id, UUID accountId, CareRules.MedicineValues values, Instant now) {
        jdbc.sql(
                        """
                        UPDATE customer_medicine SET
                          member_id = :member, product_id = :product, name = :name, dose_amount = :amount,
                          dose_unit = :unit, food = :food, weekdays = :weekdays, starts_on = :starts, ends_on = :ends,
                          stock_units = :stock, units_per_dose = :units, reminders = :reminders, note = :note,
                          updated_at = :now
                        WHERE account_id = :account AND id = :id
                        """)
                .param("member", values.memberId())
                .param("product", values.productId(), Types.VARCHAR)
                .param("name", values.name(), Types.VARCHAR)
                .param("amount", values.doseAmount())
                .param("unit", values.doseUnit(), Types.VARCHAR)
                .param("food", values.food(), Types.VARCHAR)
                .param("weekdays", values.weekdays(), Types.VARCHAR)
                .param("starts", Date.valueOf(values.startsOn()))
                .param("ends", values.endsOn() == null ? null : Date.valueOf(values.endsOn()), Types.DATE)
                .param("stock", values.stockUnits())
                .param("units", values.doseAmount())
                .param("reminders", values.reminders())
                .param("note", values.note(), Types.VARCHAR)
                .param("now", Timestamp.from(now))
                .param("account", accountId)
                .param("id", id)
                .update();
    }

    public void replaceTimes(UUID medicineId, List<LocalTime> times) {
        jdbc.sql("DELETE FROM customer_medicine_time WHERE medicine_id = :id")
                .param("id", medicineId)
                .update();
        for (LocalTime time : times) {
            jdbc.sql("INSERT INTO customer_medicine_time (medicine_id, local_time) VALUES (:id, :time)")
                    .param("id", medicineId)
                    .param("time", Time.valueOf(time))
                    .update();
        }
    }

    public List<TimeRow> times(UUID accountId) {
        return jdbc.sql(
                        """
                        SELECT t.medicine_id, t.local_time
                        FROM customer_medicine_time t
                        JOIN customer_medicine m ON m.id = t.medicine_id
                        WHERE m.account_id = :account
                        ORDER BY t.local_time
                        """)
                .param("account", accountId)
                .query(CareStore::time)
                .list();
    }

    public void addStock(UUID accountId, UUID medicineId, BigDecimal delta, Instant now) {
        jdbc.sql(
                        """
                        UPDATE customer_medicine
                        SET stock_units = GREATEST(0, stock_units + :delta), updated_at = :now
                        WHERE account_id = :account AND id = :id
                        """)
                .param("delta", delta)
                .param("now", Timestamp.from(now))
                .param("account", accountId)
                .param("id", medicineId)
                .update();
    }

    public void deletePendingFrom(UUID medicineId, LocalDate from) {
        jdbc.sql(
                        """
                        DELETE FROM customer_dose
                        WHERE medicine_id = :id AND status = 'pending' AND scheduled_on >= :from
                        """)
                .param("id", medicineId)
                .param("from", Date.valueOf(from))
                .update();
    }

    public void ensureDose(UUID id, UUID accountId, UUID medicineId, UUID memberId, LocalDate day, LocalTime time) {
        jdbc.sql(
                        """
                        INSERT INTO customer_dose (
                          id, account_id, medicine_id, member_id, scheduled_on, scheduled_at, status)
                        VALUES (:id, :account, :medicine, :member, :day, :time, 'pending')
                        ON CONFLICT (medicine_id, scheduled_on, scheduled_at) DO NOTHING
                        """)
                .param("id", id)
                .param("account", accountId)
                .param("medicine", medicineId)
                .param("member", memberId)
                .param("day", Date.valueOf(day))
                .param("time", Time.valueOf(time))
                .update();
    }

    public List<DoseRow> doses(UUID accountId, LocalDate from, LocalDate to, UUID memberId) {
        String sql = doseSelect() + " WHERE account_id = :account AND scheduled_on >= :from AND scheduled_on <= :to";
        if (memberId != null) {
            sql += " AND member_id = :member";
        }
        sql += " ORDER BY scheduled_on, scheduled_at, id";
        JdbcClient.StatementSpec spec = jdbc.sql(sql)
                .param("account", accountId)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to));
        if (memberId != null) {
            spec = spec.param("member", memberId);
        }
        return spec.query(CareStore::dose).list();
    }

    public Optional<DoseActionRow> findDose(UUID accountId, UUID id) {
        return jdbc.sql(
                        """
                        SELECT d.id, d.medicine_id, d.status, d.scheduled_on, m.units_per_dose
                        FROM customer_dose d
                        JOIN customer_medicine m ON m.id = d.medicine_id
                        WHERE d.account_id = :account AND d.id = :id
                        """)
                .param("account", accountId)
                .param("id", id)
                .query(CareStore::action)
                .optional();
    }

    public void markDose(UUID accountId, UUID id, String status, Instant actedAt) {
        jdbc.sql(
                        """
                        UPDATE customer_dose
                        SET status = :status, acted_at = :acted
                        WHERE account_id = :account AND id = :id
                        """)
                .param("status", status, Types.VARCHAR)
                .param("acted", actedAt == null ? null : Timestamp.from(actedAt), Types.TIMESTAMP)
                .param("account", accountId)
                .param("id", id)
                .update();
    }

    public List<PrescriptionRow> prescriptions(UUID accountId) {
        return jdbc.sql(
                        """
                        SELECT p.id, p.source, p.issued_on,
                          EXISTS (
                            SELECT 1 FROM customer_prescription_line l
                            WHERE l.prescription_id = p.id AND NOT l.scheduled) AS is_open
                        FROM customer_prescription p
                        WHERE p.account_id = :account
                        ORDER BY p.issued_on DESC, p.created_at DESC, p.id
                        """)
                .param("account", accountId)
                .query(CareStore::prescription)
                .list();
    }

    public Optional<PrescriptionRow> prescription(UUID accountId, UUID id) {
        return jdbc.sql(
                        """
                        SELECT p.id, p.source, p.issued_on,
                          EXISTS (
                            SELECT 1 FROM customer_prescription_line l
                            WHERE l.prescription_id = p.id AND NOT l.scheduled) AS is_open
                        FROM customer_prescription p
                        WHERE p.account_id = :account AND p.id = :id
                        """)
                .param("account", accountId)
                .param("id", id)
                .query(CareStore::prescription)
                .optional();
    }

    public Optional<UUID> latestOpen(UUID accountId) {
        return jdbc.sql(
                        """
                        SELECT p.id
                        FROM customer_prescription p
                        WHERE p.account_id = :account
                          AND EXISTS (
                            SELECT 1 FROM customer_prescription_line l
                            WHERE l.prescription_id = p.id AND NOT l.scheduled)
                        ORDER BY p.issued_on DESC, p.created_at DESC, p.id
                        LIMIT 1
                        """)
                .param("account", accountId)
                .query(UUID.class)
                .optional();
    }

    public List<LineRow> lines(UUID prescriptionId) {
        return jdbc.sql(
                        """
                        SELECT id, prescription_id, product_id, name, dose_label, scheduled
                        FROM customer_prescription_line
                        WHERE prescription_id = :prescription
                        ORDER BY name, id
                        """)
                .param("prescription", prescriptionId)
                .query(CareStore::line)
                .list();
    }

    public Optional<LineRow> line(UUID prescriptionId, UUID id) {
        return jdbc.sql(
                        """
                        SELECT id, prescription_id, product_id, name, dose_label, scheduled
                        FROM customer_prescription_line
                        WHERE prescription_id = :prescription AND id = :id
                        """)
                .param("prescription", prescriptionId)
                .param("id", id)
                .query(CareStore::line)
                .optional();
    }

    public void markLineScheduled(UUID id) {
        jdbc.sql("UPDATE customer_prescription_line SET scheduled = TRUE WHERE id = :id")
                .param("id", id)
                .update();
    }

    private static String memberSelect() {
        return """
                SELECT id, display_name, relation, age_years, birthday, conditions, allergies, mobile, consent
                FROM customer_family_member
                """;
    }

    private static String medicineSelect() {
        return """
                SELECT id, member_id, product_id, name, dose_amount, dose_unit, food, weekdays,
                       starts_on, ends_on, stock_units, units_per_dose, reminders, note
                FROM customer_medicine
                """;
    }

    private static String doseSelect() {
        return """
                SELECT id, medicine_id, member_id, scheduled_on, scheduled_at, status
                FROM customer_dose
                """;
    }

    private static MemberRow member(ResultSet row, int number) throws SQLException {
        int age = row.getInt("age_years");
        Integer ageYears = row.wasNull() ? null : age;
        Date birthday = row.getDate("birthday");
        return new MemberRow(
                row.getObject("id", UUID.class),
                row.getString("display_name"),
                row.getString("relation"),
                ageYears,
                birthday == null ? null : birthday.toLocalDate(),
                row.getString("conditions"),
                row.getString("allergies"),
                row.getString("mobile"),
                row.getBoolean("consent"));
    }

    private static MedicineRow medicine(ResultSet row, int number) throws SQLException {
        Date ends = row.getDate("ends_on");
        return new MedicineRow(
                row.getObject("id", UUID.class),
                row.getObject("member_id", UUID.class),
                row.getString("product_id"),
                row.getString("name"),
                row.getBigDecimal("dose_amount"),
                row.getString("dose_unit"),
                row.getString("food"),
                row.getString("weekdays"),
                row.getDate("starts_on").toLocalDate(),
                ends == null ? null : ends.toLocalDate(),
                row.getBigDecimal("stock_units"),
                row.getBigDecimal("units_per_dose"),
                row.getBoolean("reminders"),
                row.getString("note"));
    }

    private static TimeRow time(ResultSet row, int number) throws SQLException {
        return new TimeRow(
                row.getObject("medicine_id", UUID.class),
                row.getTime("local_time").toLocalTime());
    }

    private static DoseRow dose(ResultSet row, int number) throws SQLException {
        return new DoseRow(
                row.getObject("id", UUID.class),
                row.getObject("medicine_id", UUID.class),
                row.getObject("member_id", UUID.class),
                row.getDate("scheduled_on").toLocalDate(),
                row.getTime("scheduled_at").toLocalTime(),
                row.getString("status"));
    }

    private static DoseActionRow action(ResultSet row, int number) throws SQLException {
        return new DoseActionRow(
                row.getObject("id", UUID.class),
                row.getObject("medicine_id", UUID.class),
                row.getString("status"),
                row.getBigDecimal("units_per_dose"),
                row.getDate("scheduled_on").toLocalDate());
    }

    private static PrescriptionRow prescription(ResultSet row, int number) throws SQLException {
        return new PrescriptionRow(
                row.getObject("id", UUID.class),
                row.getString("source"),
                row.getDate("issued_on").toLocalDate(),
                row.getBoolean("is_open"));
    }

    private static LineRow line(ResultSet row, int number) throws SQLException {
        return new LineRow(
                row.getObject("id", UUID.class),
                row.getObject("prescription_id", UUID.class),
                row.getString("product_id"),
                row.getString("name"),
                row.getString("dose_label"),
                row.getBoolean("scheduled"));
    }
}
