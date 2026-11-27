package in.medmate.pharmacy_server.customer.store;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "prod", "identity-it"})
public class NearbyStore {

    private final JdbcClient jdbc;

    public NearbyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NearbyRow(
            UUID pharmacyId,
            String name,
            String address,
            String city,
            String state,
            String pincode,
            double latitude,
            double longitude,
            double distanceKm,
            boolean acceptOnlineOrders,
            String licenceNumber,
            String pharmacistName) {}

    public List<NearbyRow> nearby(double latitude, double longitude, int limit, double maxKm) {
        return jdbc.sql(
                        """
                        SELECT id, name, address, city, state, pincode, latitude, longitude,
                               accept_online_orders, distance_km, drug_licence_no, pharmacist_name
                        FROM (
                          SELECT p.id, p.name, p.address, p.city, p.state, p.pincode, p.latitude, p.longitude,
                            p.accept_online_orders, p.drug_licence_no, p.pharmacist_name,
                            (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                              cos(radians(:latitude)) * cos(radians(p.latitude))
                                * cos(radians(p.longitude) - radians(:longitude))
                              + sin(radians(:latitude)) * sin(radians(p.latitude))
                            )))) AS distance_km
                          FROM pharmacy p
                          WHERE p.verification_status = 'approved'
                            AND p.latitude IS NOT NULL
                            AND p.longitude IS NOT NULL
                        ) near
                        WHERE distance_km <= :maxKm
                        ORDER BY distance_km, id
                        LIMIT :limit
                        """)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("maxKm", maxKm)
                .param("limit", limit)
                .query((rs, row) -> nearby(rs))
                .list();
    }

    public List<NearbyRow> within(double latitude, double longitude, double maxKm) {
        return jdbc.sql(
                        """
                        SELECT id, name, address, city, state, pincode, latitude, longitude,
                               accept_online_orders, distance_km, drug_licence_no, pharmacist_name
                        FROM (
                          SELECT p.id, p.name, p.address, p.city, p.state, p.pincode, p.latitude, p.longitude,
                            p.accept_online_orders, p.drug_licence_no, p.pharmacist_name,
                            (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                              cos(radians(:latitude)) * cos(radians(p.latitude))
                                * cos(radians(p.longitude) - radians(:longitude))
                              + sin(radians(:latitude)) * sin(radians(p.latitude))
                            )))) AS distance_km
                          FROM pharmacy p
                          WHERE p.verification_status = 'approved'
                            AND p.accept_online_orders
                            AND p.latitude IS NOT NULL
                            AND p.longitude IS NOT NULL
                        ) near
                        WHERE distance_km <= :maxKm
                        ORDER BY distance_km, id
                        """)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("maxKm", maxKm)
                .query((rs, row) -> nearby(rs))
                .list();
    }

    private static NearbyRow nearby(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NearbyRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("state"),
                rs.getString("pincode"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getDouble("distance_km"),
                rs.getBoolean("accept_online_orders"),
                rs.getString("drug_licence_no"),
                rs.getString("pharmacist_name"));
    }
}
