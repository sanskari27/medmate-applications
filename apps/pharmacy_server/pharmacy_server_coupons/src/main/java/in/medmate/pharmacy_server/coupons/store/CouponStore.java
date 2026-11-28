package in.medmate.pharmacy_server.coupons.store;

import in.medmate.pharmacy_server.coupons.CouponRules;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "prod", "identity-it"})
public class CouponStore {

    private static final String COLUMNS =
            """
            c.id, c.code, c.title, c.discount_type, c.discount_value, c.max_discount, c.min_order,
            c.starts_at, c.ends_at, c.max_redemptions, c.max_per_customer, c.pharmacy_scope, c.active,
            c.banner_key, c.banner_content_type, c.created_at, c.updated_at,
            (SELECT COUNT(*) FROM coupon_redemption r WHERE r.coupon_id = c.id) AS redemption_count,
            (SELECT COUNT(*) FROM coupon_pharmacy p WHERE p.coupon_id = c.id) AS pharmacy_count
            """;

    private final JdbcClient jdbc;

    public CouponStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record CouponRow(
            UUID id,
            String code,
            String title,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscount,
            BigDecimal minOrder,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String pharmacyScope,
            boolean active,
            String bannerKey,
            String bannerContentType,
            Instant createdAt,
            Instant updatedAt,
            int redemptionCount,
            int pharmacyCount) {}

    public CouponRow insert(UUID id, CouponRules.Draft draft, String bannerKey, String bannerContentType, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO coupon (
                            id, code, title, discount_type, discount_value, max_discount, min_order,
                            starts_at, ends_at, max_redemptions, max_per_customer, pharmacy_scope, active,
                            banner_key, banner_content_type, created_at, updated_at
                        ) VALUES (
                            :id, :code, :title, :discountType, :discountValue, :maxDiscount, :minOrder,
                            :startsAt, :endsAt, :maxRedemptions, :maxPerCustomer, :pharmacyScope, :active,
                            :bannerKey, :bannerContentType, :now, :now
                        )
                        """)
                .param("id", id)
                .param("code", draft.code(), Types.VARCHAR)
                .param("title", draft.title(), Types.VARCHAR)
                .param("discountType", draft.discountType(), Types.VARCHAR)
                .param("discountValue", draft.discountValue(), Types.NUMERIC)
                .param("maxDiscount", draft.maxDiscount(), Types.NUMERIC)
                .param("minOrder", draft.minOrder(), Types.NUMERIC)
                .param("startsAt", timestamp(draft.startsAt()), Types.TIMESTAMP)
                .param("endsAt", timestamp(draft.endsAt()), Types.TIMESTAMP)
                .param("maxRedemptions", draft.maxRedemptions(), Types.INTEGER)
                .param("maxPerCustomer", draft.maxPerCustomer(), Types.INTEGER)
                .param("pharmacyScope", draft.pharmacyScope(), Types.VARCHAR)
                .param("active", draft.active(), Types.BOOLEAN)
                .param("bannerKey", bannerKey, Types.VARCHAR)
                .param("bannerContentType", bannerContentType, Types.VARCHAR)
                .param("now", Timestamp.from(now))
                .update();
        replacePharmacies(id, draft.pharmacyIds());
        return row(
                id,
                draft,
                bannerKey,
                bannerContentType,
                now,
                now,
                0,
                draft.pharmacyIds().size());
    }

    public Optional<CouponRow> update(
            UUID id, CouponRules.Draft draft, String bannerKey, String bannerContentType, Instant now) {
        int updated = jdbc.sql(
                        """
                        UPDATE coupon
                           SET title = :title,
                               discount_type = :discountType,
                               discount_value = :discountValue,
                               max_discount = :maxDiscount,
                               min_order = :minOrder,
                               starts_at = :startsAt,
                               ends_at = :endsAt,
                               max_redemptions = :maxRedemptions,
                               max_per_customer = :maxPerCustomer,
                               pharmacy_scope = :pharmacyScope,
                               active = :active,
                               banner_key = :bannerKey,
                               banner_content_type = :bannerContentType,
                               updated_at = :now
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("title", draft.title(), Types.VARCHAR)
                .param("discountType", draft.discountType(), Types.VARCHAR)
                .param("discountValue", draft.discountValue(), Types.NUMERIC)
                .param("maxDiscount", draft.maxDiscount(), Types.NUMERIC)
                .param("minOrder", draft.minOrder(), Types.NUMERIC)
                .param("startsAt", timestamp(draft.startsAt()), Types.TIMESTAMP)
                .param("endsAt", timestamp(draft.endsAt()), Types.TIMESTAMP)
                .param("maxRedemptions", draft.maxRedemptions(), Types.INTEGER)
                .param("maxPerCustomer", draft.maxPerCustomer(), Types.INTEGER)
                .param("pharmacyScope", draft.pharmacyScope(), Types.VARCHAR)
                .param("active", draft.active(), Types.BOOLEAN)
                .param("bannerKey", bannerKey, Types.VARCHAR)
                .param("bannerContentType", bannerContentType, Types.VARCHAR)
                .param("now", Timestamp.from(now))
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        replacePharmacies(id, draft.pharmacyIds());
        return find(id);
    }

    public Optional<CouponRow> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM coupon c WHERE c.id = :id")
                .param("id", id)
                .query(CouponStore::map)
                .optional();
    }

    public Optional<CouponRow> findByCode(String code) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM coupon c WHERE c.code = :code")
                .param("code", code, Types.VARCHAR)
                .query(CouponStore::map)
                .optional();
    }

    public List<CouponRow> page(int limit, int offset) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM coupon c ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", limit)
                .param("offset", offset)
                .query(CouponStore::map)
                .list();
    }

    public int count() {
        return jdbc.sql("SELECT COUNT(*) FROM coupon").query(Integer.class).single();
    }

    public List<UUID> pharmacyIds(UUID couponId) {
        return jdbc.sql(
                        """
                        SELECT pharmacy_id FROM coupon_pharmacy
                         WHERE coupon_id = :coupon
                         ORDER BY pharmacy_id
                        """)
                .param("coupon", couponId)
                .query(UUID.class)
                .list();
    }

    public List<CouponRow> accepting(UUID pharmacyId, Instant now) {
        return jdbc.sql(
                        """
                        SELECT
                        """
                                + COLUMNS
                                + """
                        FROM coupon c
                        WHERE c.active = TRUE
                          AND (c.starts_at IS NULL OR c.starts_at <= :now)
                          AND (c.ends_at IS NULL OR c.ends_at > :now)
                          AND (
                            c.pharmacy_scope = 'all'
                            OR EXISTS (
                              SELECT 1 FROM coupon_pharmacy cp
                              WHERE cp.coupon_id = c.id AND cp.pharmacy_id = :pharmacy
                            )
                          )
                        ORDER BY c.created_at DESC, c.id DESC
                        """)
                .param("now", timestamp(now))
                .param("pharmacy", pharmacyId)
                .query(CouponStore::map)
                .list();
    }

    public boolean pharmaciesExist(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<UUID> found = jdbc.sql("SELECT id FROM pharmacy WHERE id IN (:ids)")
                .param("ids", ids)
                .query(UUID.class)
                .list();
        return found.size() == ids.size();
    }

    public int redemptionsForCustomer(UUID couponId, UUID customerId) {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) FROM coupon_redemption
                         WHERE coupon_id = :coupon AND customer_id = :customer
                        """)
                .param("coupon", couponId)
                .param("customer", customerId)
                .query(Integer.class)
                .single();
    }

    private void replacePharmacies(UUID couponId, List<UUID> pharmacyIds) {
        jdbc.sql("DELETE FROM coupon_pharmacy WHERE coupon_id = :coupon")
                .param("coupon", couponId)
                .update();
        if (pharmacyIds == null) {
            return;
        }
        for (UUID pharmacyId : pharmacyIds) {
            jdbc.sql("INSERT INTO coupon_pharmacy (coupon_id, pharmacy_id) VALUES (:coupon, :pharmacy)")
                    .param("coupon", couponId)
                    .param("pharmacy", pharmacyId)
                    .update();
        }
    }

    private static CouponRow row(
            UUID id,
            CouponRules.Draft draft,
            String bannerKey,
            String bannerContentType,
            Instant createdAt,
            Instant updatedAt,
            int redemptionCount,
            int pharmacyCount) {
        return new CouponRow(
                id,
                draft.code(),
                draft.title(),
                draft.discountType(),
                draft.discountValue(),
                draft.maxDiscount(),
                draft.minOrder(),
                draft.startsAt(),
                draft.endsAt(),
                draft.maxRedemptions(),
                draft.maxPerCustomer(),
                draft.pharmacyScope(),
                draft.active(),
                bannerKey,
                bannerContentType,
                createdAt,
                updatedAt,
                redemptionCount,
                pharmacyCount);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static CouponRow map(ResultSet row, int ignored) throws SQLException {
        return new CouponRow(
                row.getObject("id", UUID.class),
                row.getString("code"),
                row.getString("title"),
                row.getString("discount_type"),
                row.getBigDecimal("discount_value"),
                row.getBigDecimal("max_discount"),
                row.getBigDecimal("min_order"),
                instant(row, "starts_at"),
                instant(row, "ends_at"),
                integer(row, "max_redemptions"),
                integer(row, "max_per_customer"),
                row.getString("pharmacy_scope"),
                row.getBoolean("active"),
                row.getString("banner_key"),
                row.getString("banner_content_type"),
                instant(row, "created_at"),
                instant(row, "updated_at"),
                row.getInt("redemption_count"),
                row.getInt("pharmacy_count"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer integer(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
