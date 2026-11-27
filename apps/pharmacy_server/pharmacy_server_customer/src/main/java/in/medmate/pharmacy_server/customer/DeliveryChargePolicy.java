package in.medmate.pharmacy_server.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DeliveryChargePolicy {

    private DeliveryChargePolicy() {}

    public record Quote(BigDecimal amount, double distanceKm) {}

    public static Quote quote(double distanceKm) {
        BigDecimal amount = distanceKm > 3
                ? new BigDecimal("20.00")
                : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        return new Quote(amount, distanceKm);
    }
}
