package in.medmate.pharmacy_server.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.catalog.CatalogDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
class CareIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

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
            statement.execute("TRUNCATE account, otp_challenge CASCADE");
        }
        try (Connection connection = catalogDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE drugs, otc_fmcg CASCADE");
            statement.execute(
                    """
                    INSERT INTO drugs (product_id, product_name, display_name, mrp, prescription_required)
                    VALUES ('CARE1', 'Telma-R 5 Tablet', 'Telma-R', 10.00, true)
                    """);
        }
        sms.clear();
    }

    @Test
    void aCustomerKeepsAFamilyScheduleAndConfirmsAPrescription() throws Exception {
        JsonNode pharmacySession = verify("9822001801", "pharmacy", "signup");
        JsonNode customerSession = verify("9822001802", "customer", "login");
        String customerId = customerSession.get("account").get("id").asText();
        String customer = token(customerSession);

        ResponseEntity<JsonNode> forbidden = rest.exchange(
                "/api/v1/customer/family",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token(pharmacySession))),
                JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode family = exchange(customer, HttpMethod.GET, "/api/v1/customer/family", null, HttpStatus.OK);
        assertThat(family.get("members")).hasSize(1);
        assertThat(family.get("members").get(0).get("relation").asText()).isEqualTo("self");
        String self = family.get("members").get(0).get("id").asText();

        JsonNode mother = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/family",
                Map.of(
                        "displayName",
                        "Amma",
                        "relation",
                        "mother",
                        "ageYears",
                        62,
                        "conditions",
                        List.of("diabetes"),
                        "allergies",
                        List.of(),
                        "consent",
                        true),
                HttpStatus.CREATED);
        assertThat(mother.get("member").get("displayName").asText()).isEqualTo("Amma");

        String today = LocalDate.now(ZONE).toString();
        JsonNode created = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/medicines",
                Map.ofEntries(
                        Map.entry("memberId", self),
                        Map.entry("productId", "CARE1"),
                        Map.entry("name", "Telma-R 5 Tablet"),
                        Map.entry("doseAmount", 1),
                        Map.entry("doseUnit", "tablet"),
                        Map.entry("food", "after"),
                        Map.entry("weekdays", "1111111"),
                        Map.entry("startsOn", today),
                        Map.entry("stockUnits", 10),
                        Map.entry("reminders", true),
                        Map.entry("times", List.of("08:00", "20:00"))),
                HttpStatus.CREATED);
        assertThat(created.get("medicine").get("supplyDays").asInt()).isEqualTo(5);
        String medicineId = created.get("medicine").get("id").asText();

        JsonNode unknown = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/medicines",
                Map.of(
                        "memberId",
                        self,
                        "productId",
                        "MISSING",
                        "name",
                        "Missing",
                        "doseAmount",
                        1,
                        "doseUnit",
                        "tablet",
                        "food",
                        "after",
                        "weekdays",
                        "1111111",
                        "startsOn",
                        today,
                        "stockUnits",
                        1,
                        "times",
                        List.of("08:00")),
                HttpStatus.BAD_REQUEST);
        assertThat(unknown.get("detail").asText()).isEqualTo("Medicine was not found in the catalogue");

        JsonNode board = exchange(customer, HttpMethod.GET, "/api/v1/customer/dosage", null, HttpStatus.OK);
        assertThat(board.get("progress").get("total").asInt()).isEqualTo(2);
        assertThat(board.get("lowStock").get("title").asText()).contains("Telma-R 5 Tablet");
        String doseId = board.get("groups").get(0).get("doses").get(0).get("id").asText();

        JsonNode taken = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/dosage/doses/" + doseId + "/taken",
                Map.of(),
                HttpStatus.OK);
        assertThat(taken.get("dose").get("status").asText()).isEqualTo("taken");
        JsonNode afterTake =
                exchange(customer, HttpMethod.GET, "/api/v1/customer/medicines/" + medicineId, null, HttpStatus.OK);
        assertThat(new BigDecimal(afterTake.get("medicine").get("stockUnits").asText()))
                .isEqualByComparingTo("9");

        exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/dosage/doses/" + doseId + "/pending",
                Map.of(),
                HttpStatus.OK);
        JsonNode restored =
                exchange(customer, HttpMethod.GET, "/api/v1/customer/medicines/" + medicineId, null, HttpStatus.OK);
        assertThat(new BigDecimal(restored.get("medicine").get("stockUnits").asText()))
                .isEqualByComparingTo("10");

        JsonNode skipped = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/dosage/doses/" + doseId + "/skipped",
                Map.of(),
                HttpStatus.OK);
        assertThat(skipped.get("dose").get("status").asText()).isEqualTo("skipped");

        String prescriptionId = "00000000-0000-4000-8000-0000000000c1";
        String lineId = "00000000-0000-4000-8000-0000000000c2";
        prescribe(customerId, prescriptionId, lineId);
        JsonNode detail = exchange(
                customer, HttpMethod.GET, "/api/v1/customer/prescriptions/" + prescriptionId, null, HttpStatus.OK);
        assertThat(detail.get("prescription").get("lines").get(0).get("rx").asBoolean())
                .isTrue();

        JsonNode scheduled = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/prescriptions/" + prescriptionId + "/schedules",
                Map.of(
                        "memberId",
                        self,
                        "lines",
                        List.of(Map.of(
                                "lineId",
                                lineId,
                                "doseAmount",
                                1,
                                "doseUnit",
                                "tablet",
                                "food",
                                "after",
                                "weekdays",
                                "1111111",
                                "days",
                                5,
                                "stockUnits",
                                5,
                                "times",
                                List.of("08:00")))),
                HttpStatus.OK);
        assertThat(scheduled.get("scheduled").asInt()).isEqualTo(1);
        JsonNode again = exchange(
                customer,
                HttpMethod.POST,
                "/api/v1/customer/prescriptions/" + prescriptionId + "/schedules",
                Map.of(
                        "memberId",
                        self,
                        "lines",
                        List.of(Map.of(
                                "lineId",
                                lineId,
                                "doseAmount",
                                1,
                                "doseUnit",
                                "tablet",
                                "food",
                                "after",
                                "weekdays",
                                "1111111",
                                "days",
                                5,
                                "stockUnits",
                                5,
                                "times",
                                List.of("08:00")))),
                HttpStatus.BAD_REQUEST);
        assertThat(again.get("detail").asText()).isEqualTo("This medicine is already on a schedule");
    }

    private void prescribe(String accountId, String prescriptionId, String lineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    INSERT INTO customer_prescription (id, account_id, source, issued_on, created_at)
                    VALUES ('%s', '%s', 'MedMate doctor', CURRENT_DATE, NOW())
                    """
                            .formatted(prescriptionId, accountId));
            statement.execute(
                    """
                    INSERT INTO customer_prescription_line (id, prescription_id, product_id, name, dose_label, scheduled)
                    VALUES ('%s', '%s', 'CARE1', 'Telma-R 5 Tablet', '1 tablet', FALSE)
                    """
                            .formatted(lineId, prescriptionId));
        }
    }

    private JsonNode exchange(String token, HttpMethod method, String path, Object body, HttpStatus status) {
        ResponseEntity<JsonNode> response =
                rest.exchange(path, method, new HttpEntity<>(body, bearer(token)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(status);
        return response.getBody();
    }

    private JsonNode verify(String phone, String type, String purpose) {
        ResponseEntity<JsonNode> sent = rest.exchange(
                "/auth/otp/send",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("phone", phone, "accountType", type, "purpose", purpose), json()),
                JsonNode.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = sms.latestCode("+91" + phone);
        ResponseEntity<JsonNode> response = rest.exchange(
                "/auth/otp/verify",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("phone", phone, "accountType", type, "purpose", purpose, "code", code), json()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
