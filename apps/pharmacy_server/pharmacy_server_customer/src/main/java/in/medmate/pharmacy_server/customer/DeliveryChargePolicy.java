package in.medmate.pharmacy_server.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DeliveryChargePolicy {

    private DeliveryChargePolicy() {}

    public record Quote(BigDecimal amount, double distanceKm) {}

    public static Quote quote(double distanceKm) {
        return new Quote(BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY), distanceKm);
    }
}
