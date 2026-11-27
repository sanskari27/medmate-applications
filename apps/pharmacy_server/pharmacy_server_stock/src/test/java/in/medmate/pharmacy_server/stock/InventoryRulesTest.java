package in.medmate.pharmacy_server.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryRulesTest {

    private final YearMonth november = YearMonth.of(2026, 11);

    @Test
    void classifiesLowOutAndExpiringStock() {
        InventoryRules.Batch fresh =
                new InventoryRules.Batch("2027-08", 5, new BigDecimal("10.00"), new BigDecimal("18.00"));
        InventoryRules.Batch soon =
                new InventoryRules.Batch("2027-03", 2, new BigDecimal("4.00"), new BigDecimal("9.00"));
        InventoryRules.Batch past =
                new InventoryRules.Batch("2026-01", 1, new BigDecimal("3.00"), new BigDecimal("6.00"));

        assertThat(InventoryRules.low(5, 20)).isTrue();
        assertThat(InventoryRules.low(21, 20)).isFalse();
        assertThat(InventoryRules.out(0)).isTrue();
        assertThat(InventoryRules.low(0, 20)).isFalse();
        assertThat(InventoryRules.pricedLow(1, new BigDecimal("999.49"))).isTrue();
        assertThat(InventoryRules.pricedLow(1, new BigDecimal("999.50"))).isFalse();
        assertThat(InventoryRules.pricedLow(0, new BigDecimal("1.00"))).isFalse();
        assertThat(InventoryRules.listFilter(null)).isEqualTo("all");
        assertThat(InventoryRules.listFilter("expiring")).isEqualTo("expiring");
        assertThat(InventoryRules.listFilter("expired")).isEqualTo("expired");
        assertThat(InventoryRules.hasExpiring(List.of(fresh), november)).isFalse();
        assertThat(InventoryRules.hasExpiring(List.of(soon, past), november)).isTrue();
        assertThat(InventoryRules.hasExpiring(List.of(past), november)).isFalse();
        assertThat(InventoryRules.expiryLimit(november)).isEqualTo(YearMonth.of(2027, 3));
        assertThat(InventoryRules.cost(List.of(soon, past))).isEqualByComparingTo("11.00");
        assertThat(InventoryRules.retail(List.of(soon))).isEqualByComparingTo("18.00");
        assertThat(InventoryRules.expiringCost(List.of(fresh, soon), november)).isEqualByComparingTo("8.00");
        assertThat(InventoryRules.units(List.of(fresh, soon))).isEqualTo(7);
    }

    @Test
    void onlinePriceMustNotExceedOnlineMrp() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode body = json.createObjectNode();
        body.put("onlineMrp", "120.00");
        body.put("onlinePrice", "99.00");

        InventoryRules.OnlinePrice saved = InventoryRules.onlinePrice(body);
        assertThat(saved.problem()).isNull();
        assertThat(saved.onlineMrp()).isEqualByComparingTo("120.00");
        assertThat(saved.onlinePrice()).isEqualByComparingTo("99.00");

        body.put("onlinePrice", "121.00");
        assertThat(InventoryRules.onlinePrice(body).problem()).isEqualTo("Online price cannot exceed online MRP");

        body.put("onlineMrp", "-1");
        assertThat(InventoryRules.onlinePrice(body).problem()).isEqualTo("Online MRP is not valid");

        body.remove("onlineMrp");
        assertThat(InventoryRules.onlinePrice(body).problem()).isEqualTo("Online MRP is not valid");
    }
}
