package in.medmate.pharmacy_server.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.catalog.CatalogDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("identity-it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CartIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CatalogDatabase.register(registry);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private in.medmate.pharmacy_server.identity.sms.RecordingSmsSender sms;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    @Qualifier("catalogDataSource")
    private javax.sql.DataSource catalogDataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE account, otp_challenge, coupon CASCADE");
        }
        try (Connection connection = catalogDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE drugs, otc_fmcg CASCADE");
            statement.execute(
                    """
                    INSERT INTO drugs (product_id, product_name, display_name, mrp, prescription_required)
                    VALUES ('CART1', 'Crocin Advance', 'Crocin', 25.50, true),
                           ('CART2', 'Vitamin Bottle', 'Vitamin', 40.00, false)
                    """);
        }
        sms.clear();
    }

    @Test
    void aCartGroupsNearbyPharmaciesAndQuotesACouponPerGroup() throws Exception {
        JsonNode nearSession = verify("9822001901", "pharmacy", "signup");
        JsonNode partialSession = verify("9822001902", "pharmacy", "signup");
        JsonNode farSession = verify("9822001903", "pharmacy", "signup");
        JsonNode customerSession = verify("9822001904", "customer", "login");
        String near = nearSession.get("account").get("pharmacyId").asText();
        String partial = partialSession.get("account").get("pharmacyId").asText();
        String far = farSession.get("account").get("pharmacyId").asText();
        String customerId = customerSession.get("account").get("id").asText();
        place(near, 12.9800, 77.6000);
        place(partial, 13.0200, 77.6200);
        place(far, 13.5000, 77.5946);
        stock(near, "CART1", "CART2");
        stock(partial, "CART1");
        stock(far, "CART1", "CART2");
        address(customerId);

        String pharmacyToken = token(nearSession);
        ResponseEntity<JsonNode> forbidden = rest.exchange(
                "/api/v1/customer/cart", HttpMethod.GET, new HttpEntity<>(bearer(pharmacyToken)), JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String customer = token(customerSession);
        put(customer, "/api/v1/customer/cart/lines", Map.of("productId", "CART1", "qty", 2));
        JsonNode cart = put(customer, "/api/v1/customer/cart/lines", Map.of("productId", "CART2", "qty", 1));
        assertThat(cart.get("notice").isNull()).isTrue();
        assertThat(cart.get("selectionSource").asText()).isEqualTo("recommended");
        assertThat(assigned(cart, "CART1")).isEqualTo(near);
        assertThat(assigned(cart, "CART2")).isEqualTo(near);
        assertThat(pharmacyIds(cart)).contains(near, partial).doesNotContain(far);
        assertThat(alternativeIds(cart, "CART1")).contains(near, partial).doesNotContain(far);
        assertThat(alternativeIds(cart, "CART2")).containsExactly(near);
        assertThat(pharmacy(cart, near).get("selected").asBoolean()).isTrue();
        assertThat(pharmacy(cart, partial).get("recommended").asBoolean()).isFalse();

        JsonNode quoted = put(customer, "/api/v1/customer/cart/coupon", Map.of("code", "save50"));
        JsonNode nearGroup = pharmacy(quoted, near);
        assertThat(nearGroup.get("merchandise").asText()).isEqualTo("270.00");
        assertThat(nearGroup.get("discount").asText()).isEqualTo("50.00");
        assertThat(nearGroup.get("deliveryCharge").get("amount").asText()).isEqualTo("0.00");
        assertThat(nearGroup.get("payable").asText()).isEqualTo("220.00");
        assertThat(quoted.get("totals").get("payable").asText()).isEqualTo("220.00");

        JsonNode chosen =
                put(customer, "/api/v1/customer/cart/pharmacies", Map.of("pharmacyIds", java.util.List.of(partial)));
        assertThat(chosen.get("selectionSource").asText()).isEqualTo("chosen");
        assertThat(assigned(chosen, "CART1")).isEqualTo(partial);
        assertThat(line(chosen, "CART2").get("available").asBoolean()).isFalse();
        assertThat(pharmacy(chosen, partial).get("discount").asText()).isEqualTo("50.00");
    }

    private void place(String pharmacyId, double latitude, double longitude) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE pharmacy SET verification_status = 'approved', accept_online_orders = TRUE, "
                    + "latitude = " + latitude + ", longitude = " + longitude + " WHERE id = '" + pharmacyId + "'");
        }
    }

    private void stock(String pharmacyId, String... productIds) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String productId : productIds) {
                statement.execute(
                        "INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level, online_mrp, online_price) VALUES ('"
                                + pharmacyId + "', '" + productId + "', '" + productId + "', 1, 100.00, 90.00)");
                statement.execute(
                        "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                                + pharmacyId + "', '" + productId + "', 'B1', '2099-01', 10, 10.00, 30.00)");
            }
        }
    }

    private void address(String accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    INSERT INTO customer_delivery_address (
                      id, account_id, label, line1, line2, city, pincode, latitude, longitude,
                      receiver_name, receiver_phone, selected, created_at, updated_at)
                    VALUES (
                      '00000000-0000-4000-8000-000000000091', '%s', 'home', '12 Residency Road', '',
                      'Bengaluru', '560001', 12.9716, 77.5946, 'Priya', '9845012345', TRUE, NOW(), NOW())
                    """
                            .formatted(accountId));
            statement.execute(
                    """
                    INSERT INTO coupon (
                      id, code, title, discount_type, discount_value, pharmacy_scope, active,
                      banner_key, banner_content_type, created_at, updated_at)
                    VALUES (
                      '00000000-0000-4000-8000-000000000092', 'SAVE50', 'Save 50', 'flat', 50.00, 'all', TRUE,
                      'banner', 'image/png', NOW(), NOW())
                    """);
        }
    }

    private JsonNode put(String token, String path, Object body) {
        ResponseEntity<JsonNode> response =
                rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, bearer(token)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static String assigned(JsonNode cart, String productId) {
        JsonNode line = line(cart, productId);
        return line.get("assignedPharmacyId").asText();
    }

    private static JsonNode line(JsonNode cart, String productId) {
        for (JsonNode line : cart.get("lines")) {
            if (productId.equals(line.get("productId").asText())) {
                return line;
            }
        }
        throw new AssertionError(productId);
    }

    private static JsonNode pharmacy(JsonNode cart, String pharmacyId) {
        for (JsonNode pharmacy : cart.get("pharmacies")) {
            if (pharmacyId.equals(pharmacy.get("pharmacyId").asText())) {
                return pharmacy;
            }
        }
        throw new AssertionError(pharmacyId);
    }

    private static java.util.List<String> pharmacyIds(JsonNode cart) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        cart.get("pharmacies")
                .forEach(pharmacy -> ids.add(pharmacy.get("pharmacyId").asText()));
        return ids;
    }

    private static java.util.List<String> alternativeIds(JsonNode cart, String productId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        line(cart, productId)
                .get("alternatives")
                .forEach(offer -> ids.add(offer.get("pharmacyId").asText()));
        return ids;
    }

    private JsonNode verify(String phone, String type, String purpose) {
        String code = send(phone, type, purpose);
        ResponseEntity<JsonNode> response = rest.exchange(
                "/auth/otp/verify",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("phone", phone, "accountType", type, "purpose", purpose, "code", code), json()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String send(String phone, String type, String purpose) {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/auth/otp/send",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("phone", phone, "accountType", type, "purpose", purpose), json()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = sms.latestCode("+91" + phone);
        assertThat(code).isNotBlank();
        return code;
    }

    private static String token(JsonNode session) {
        return session.get("accessToken").asText();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = json();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
