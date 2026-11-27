package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryChargePolicyTest {

    @Test
    void chargeStaysZeroUntilTheFormulaIsSet() {
        DeliveryChargePolicy.Quote quote = DeliveryChargePolicy.quote(12.5);

        assertThat(quote.amount()).isEqualByComparingTo("0.00");
        assertThat(quote.distanceKm()).isEqualTo(12.5);
    }
}
