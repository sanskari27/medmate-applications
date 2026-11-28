package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.catalog.CatalogDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
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
class AvailableCatalogIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CatalogDatabase.register(registry);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private in.medmate.pharmacy_server.identity.sms.RecordingSmsSender sms;

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Qualifier("catalogDataSource")
    private DataSource catalogDataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE account, otp_challenge, app_section CASCADE");
        }
        try (Connection connection = catalogDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE drugs, otc_fmcg CASCADE");
            statement.execute(
                    """
                    INSERT INTO drugs (
                      product_id, product_name, display_name, mrp, prescription_required,
                      marketer_name, package, product_form)
                    VALUES ('IN1', 'Crocin Advance', 'Crocin', 25.50, true, 'GSK', 'Strip of 15 tablets', 'Tablet')
                    """);
            statement.execute(
                    """
                    INSERT INTO drug_ingredients (product_id, position, name, strength)
                    VALUES ('IN1', 1, 'Paracetamol', '650mg')
                    """);
            statement.execute("INSERT INTO drug_categories (product_id, position, segment) VALUES ('IN1', 0, 'Pain')");
            statement.execute(
                    "INSERT INTO drug_images (product_id, position, url) VALUES ('IN1', 1, 'https://img.example/later.jpg')");
            statement.execute(
                    "INSERT INTO drug_images (product_id, position, url) VALUES ('IN1', 0, 'https://img.example/crocin.jpg')");
            statement.execute(
                    """
                    INSERT INTO drugs (product_id, product_name, display_name, mrp, prescription_required)
                    VALUES ('OUT1', 'Missing Stock', 'Missing', 10.00, false)
                    """);
            statement.execute("INSERT INTO drug_categories (product_id, position, segment) VALUES ('OUT1', 0, 'Skin')");
            statement.execute(
                    """
                    INSERT INTO drugs (product_id, product_name, display_name, mrp, prescription_required)
                    VALUES ('EXP1', 'Expired Tablet', 'Expired', 12.00, true)
                    """);
            statement.execute("INSERT INTO drug_categories (product_id, position, segment) VALUES ('EXP1', 0, 'Pain')");
            statement.execute(
                    """
                    INSERT INTO otc_fmcg (product_id, product_name, display_name, mrp)
                    VALUES ('FMCG1', 'Daily Wash', 'Wash', 99.00)
                    """);
            statement.execute(
                    "INSERT INTO otc_fmcg_categories (product_id, position, segment) VALUES ('FMCG1', 0, 'Pain')");
            statement.execute(
                    "INSERT INTO otc_fmcg_images (product_id, position, url) VALUES ('FMCG1', 0, 'https://img.example/wash.jpg')");
            statement.execute(
                    """
                    INSERT INTO drugs (product_id, product_name, display_name, mrp, prescription_required)
                    VALUES ('NOIMG', 'Aaa Plain', 'Plain', 8.00, false)
                    """);
            statement.execute(
                    "INSERT INTO drug_categories (product_id, position, segment) VALUES ('NOIMG', 0, 'Pain')");
        }
        sms.clear();
    }

    @Test
    void aCustomerSeesOnlyInStockCatalogProducts() throws Exception {
        JsonNode pharmacy = verify("9822001801", "pharmacy", "signup", false);
        String pharmacyId = pharmacy.get("account").get("pharmacyId").asText();
        approve(pharmacyId);
        stock(pharmacyId);

        ResponseEntity<JsonNode> guest =
                rest.exchange("/api/v1/customer/catalog", HttpMethod.GET, new HttpEntity<>(json()), JsonNode.class);
        assertThat(guest.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> forbidden = rest.exchange(
                "/api/v1/customer/catalog", HttpMethod.GET, new HttpEntity<>(bearer(token(pharmacy))), JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("detail").asText()).isEqualTo("This action is not allowed.");

        String customer = token(verify("9822001802", "customer", "login", false));
        JsonNode page = exchange(customer, "/api/v1/customer/catalog", HttpStatus.OK);
        assertThat(page.get("total").asInt()).isEqualTo(3);
        assertThat(ids(page)).containsExactly("IN1", "FMCG1", "NOIMG");
        JsonNode drug = page.get("items").get(0);
        assertThat(drug.get("name").asText()).isEqualTo("Crocin Advance");
        assertThat(drug.get("displayName").asText()).isEqualTo("Crocin");
        assertThat(drug.get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(drug.get("image").asText()).isEqualTo("https://img.example/crocin.jpg");
        assertThat(drug.get("rxRequired").asBoolean()).isTrue();
        assertThat(drug.get("marketerName").asText()).isEqualTo("GSK");
        assertThat(drug.get("pack").asText()).isEqualTo("Strip of 15 tablets");
        assertThat(drug.get("productForm").asText()).isEqualTo("Tablet");
        assertThat(drug.get("composition").asText()).isEqualTo("Paracetamol (650mg)");
        assertThat(drug.get("pharmacyId").isNull()).isTrue();
        assertThat(drug.get("deliveryCharge").isNull()).isTrue();
        assertThat(page.get("items").get(1).get("rxRequired").asBoolean()).isFalse();
        assertThat(page.get("items").get(1).get("composition").isNull()).isTrue();
        assertThat(page.get("items").get(1).get("image").asText()).isEqualTo("https://img.example/wash.jpg");

        assertThat(ids(exchange(customer, "/api/v1/customer/catalog?category=pain", HttpStatus.OK)))
                .containsExactly("IN1", "FMCG1", "NOIMG");
        assertThat(exchange(customer, "/api/v1/customer/catalog?q=plain", HttpStatus.OK)
                        .get("items")
                        .get(0)
                        .get("image")
                        .isNull())
                .isTrue();
        assertThat(ids(exchange(customer, "/api/v1/customer/catalog?q=crocin", HttpStatus.OK)))
                .containsExactly("IN1");
        assertThat(ids(exchange(customer, "/api/v1/customer/catalog?category=Skin&q=wash", HttpStatus.OK)))
                .isEmpty();
        ResponseEntity<JsonNode> shortQuery = rest.exchange(
                "/api/v1/customer/catalog?q=a", HttpMethod.GET, new HttpEntity<>(bearer(customer)), JsonNode.class);
        assertThat(shortQuery.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(shortQuery.getBody().get("detail").asText()).isEqualTo("Query is not valid");

        String admin = token(verify("9822001803", "admin", "login", true));
        JsonNode categories = exchange(admin, "/api/v1/catalog/categories", HttpStatus.OK);
        assertThat(names(categories.get("categories"))).containsExactly("Pain", "Skin");
    }

    @Test
    void aNearbyOnlineListingAddsADeliveryCharge() throws Exception {
        JsonNode pharmacy = verify("9822001811", "pharmacy", "signup", false);
        String pharmacyId = pharmacy.get("account").get("pharmacyId").asText();
        approve(pharmacyId);
        stock(pharmacyId);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE pharmacy SET accept_online_orders = TRUE, latitude = 12.9800, longitude = 77.6000 "
                            + "WHERE id = '" + pharmacyId + "'");
            statement.execute("UPDATE inventory_product SET online_mrp = 120.00, online_price = 99.00 "
                    + "WHERE pharmacy_id = '" + pharmacyId + "' AND product_id = 'IN1'");
        }
        JsonNode customer = verify("9822001812", "customer", "login", false);
        String accountId = customer.get("account").get("id").asText();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    INSERT INTO customer_delivery_address (
                      id, account_id, label, line1, line2, city, pincode, latitude, longitude,
                      receiver_name, receiver_phone, selected, created_at, updated_at)
                    VALUES (
                      '00000000-0000-4000-8000-000000000081', '%s', 'home', '12 Residency Road', '',
                      'Bengaluru', '560001', 12.9716, 77.5946, 'Priya', '9845012345', TRUE, NOW(), NOW())
                    """
                            .formatted(accountId));
        }

        JsonNode page = exchange(token(customer), "/api/v1/customer/catalog?q=crocin", HttpStatus.OK);
        JsonNode drug = page.get("items").get(0);
        assertThat(drug.get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(drug.get("pharmacyId").asText()).isEqualTo(pharmacyId);
        assertThat(drug.get("onlineMrp").asText()).isEqualTo("120.00");
        assertThat(drug.get("onlinePrice").asText()).isEqualTo("99.00");
        assertThat(drug.get("deliveryCharge").get("amount").asText()).isEqualTo("0.00");
        assertThat(drug.get("distanceKm").asDouble()).isPositive();
    }

    private void stock(String pharmacyId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level) VALUES ('"
                    + pharmacyId + "', 'IN1', 'Crocin Advance', 1)");
            statement.execute(
                    "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                            + pharmacyId + "', 'IN1', 'B1', '2099-01', 4, 10.00, 30.00)");
            statement.execute("INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level) VALUES ('"
                    + pharmacyId + "', 'OUT1', 'Missing Stock', 1)");
            statement.execute(
                    "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                            + pharmacyId + "', 'OUT1', 'B1', '2099-01', 0, 10.00, 30.00)");
            statement.execute("INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level) VALUES ('"
                    + pharmacyId + "', 'EXP1', 'Expired Tablet', 1)");
            statement.execute(
                    "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                            + pharmacyId + "', 'EXP1', 'B1', '2000-01', 5, 10.00, 30.00)");
            statement.execute("INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level) VALUES ('"
                    + pharmacyId + "', 'NOIMG', 'Aaa Plain', 1)");
            statement.execute(
                    "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                            + pharmacyId + "', 'NOIMG', 'B1', '2099-01', 3, 4.00, 8.00)");
            statement.execute("INSERT INTO inventory_product (pharmacy_id, product_id, name, reorder_level) VALUES ('"
                    + pharmacyId + "', 'FMCG1', 'Daily Wash', 1)");
            statement.execute(
                    "INSERT INTO inventory_batch (pharmacy_id, product_id, batch_no, expiry, qty, rate, mrp) VALUES ('"
                            + pharmacyId + "', 'FMCG1', 'B1', '2099-01', 2, 10.00, 40.00)");
        }
    }

    private void approve(String pharmacyId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE pharmacy SET verification_status = 'approved' WHERE id = '" + pharmacyId + "'");
        }
    }

    private JsonNode exchange(String token, String path, HttpStatus status) {
        ResponseEntity<JsonNode> response =
                rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(status);
        return response.getBody();
    }

    private static List<String> ids(JsonNode page) {
        return names(page.get("items"), "productId");
    }

    private static List<String> names(JsonNode rows) {
        return names(rows, null);
    }

    private static List<String> names(JsonNode rows, String field) {
        List<String> values = new ArrayList<>();
        rows.forEach(
                row -> values.add(field == null ? row.asText() : row.get(field).asText()));
        return values;
    }

    private JsonNode verify(String phone, String type, String purpose, boolean bootstrap) {
        if (bootstrap) {
            HttpHeaders headers = json();
            headers.setBearerAuth("test-bootstrap");
            ResponseEntity<JsonNode> created = rest.exchange(
                    "/auth/admin-bootstrap",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("phone", phone), headers),
                    JsonNode.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
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
