package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryChargePolicyTest {

    @Test
    void deliveryIsFreeWithinThreeKilometresAndTwentyBeyond() {
        assertThat(DeliveryChargePolicy.quote(0.4).amount()).isEqualByComparingTo("0.00");
        assertThat(DeliveryChargePolicy.quote(3).amount()).isEqualByComparingTo("0.00");
        DeliveryChargePolicy.Quote beyond = DeliveryChargePolicy.quote(3.001);
        assertThat(beyond.amount()).isEqualByComparingTo("20.00");
        assertThat(beyond.distanceKm()).isEqualTo(3.001);
        assertThat(DeliveryChargePolicy.quote(12.5).amount()).isEqualByComparingTo("20.00");
    }
}
