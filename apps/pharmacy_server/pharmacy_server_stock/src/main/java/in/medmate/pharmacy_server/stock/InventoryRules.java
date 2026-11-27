package in.medmate.pharmacy_server.stock;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class InventoryRules {

    public static final String READ = "inventory:read";
    public static final String UPDATE = "inventory:update";
    public static final String EXPORT = "inventory:export";
    public static final int DEFAULT_REORDER = 20;

    private InventoryRules() {}

    public record Batch(String expiry, int qty, BigDecimal rate, BigDecimal mrp) {}

    public record OnlinePrice(BigDecimal onlineMrp, BigDecimal onlinePrice, String problem) {}

    public static OnlinePrice onlinePrice(JsonNode body) {
        if (body == null || !body.isObject()) {
            return new OnlinePrice(null, null, "Online MRP is required");
        }
        BigDecimal mrp = money(body.get("onlineMrp"));
        BigDecimal price = money(body.get("onlinePrice"));
        if (mrp == null) {
            return new OnlinePrice(null, null, "Online MRP is not valid");
        }
        if (price == null) {
            return new OnlinePrice(null, null, "Online price is not valid");
        }
        if (price.compareTo(mrp) > 0) {
            return new OnlinePrice(null, null, "Online price cannot exceed online MRP");
        }
        return new OnlinePrice(mrp, price, null);
    }

    public static int units(List<Batch> batches) {
        int total = 0;
        if (batches == null) {
            return 0;
        }
        for (Batch batch : batches) {
            if (batch != null) {
                total += Math.max(batch.qty(), 0);
            }
        }
        return total;
    }

    public static boolean out(int units) {
        return units == 0;
    }

    public static boolean low(int units, int reorderLevel) {
        return units > 0 && units <= reorderLevel;
    }

    public static boolean pricedLow(int units, BigDecimal cost) {
        if (units <= 0 || cost == null) {
            return false;
        }
        return cost.setScale(0, RoundingMode.HALF_UP).longValue() < 1000;
    }

    public static String listFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        String value = raw.trim();
        if (value.equals("all")
                || value.equals("low")
                || value.equals("out")
                || value.equals("expiring")
                || value.equals("expired")) {
            return value;
        }
        throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", "Filter is not valid");
    }

    public static YearMonth expiryLimit(YearMonth month) {
        return month.plusMonths(4);
    }

    public static boolean expiring(List<Batch> batches, YearMonth month) {
        return expiringCost(batches, month).signum() > 0 || hasExpiring(batches, month);
    }

    public static boolean hasExpiring(List<Batch> batches, YearMonth month) {
        if (batches == null) {
            return false;
        }
        YearMonth limit = expiryLimit(month);
        for (Batch batch : batches) {
            if (batch != null && batch.qty() > 0) {
                YearMonth expiry = yearMonth(batch.expiry());
                if (!expiry.isBefore(month) && !expiry.isAfter(limit)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static BigDecimal cost(List<Batch> batches) {
        return sum(batches, false, null);
    }

    public static BigDecimal retail(List<Batch> batches) {
        BigDecimal total = BigDecimal.ZERO;
        if (batches == null) {
            return total;
        }
        for (Batch batch : batches) {
            if (batch != null && batch.qty() > 0 && batch.mrp() != null) {
                total = total.add(batch.mrp().multiply(BigDecimal.valueOf(batch.qty())));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal expiringCost(List<Batch> batches, YearMonth month) {
        return sum(batches, true, month);
    }

    public static YearMonth yearMonth(String expiry) {
        return YearMonth.parse(expiry);
    }

    private static BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            BigDecimal value = node.isNumber()
                    ? node.decimalValue()
                    : new BigDecimal(node.asText().trim());
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
                return null;
            }
            if (value.precision() - value.scale() > 10) {
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private static BigDecimal sum(List<Batch> batches, boolean expiringOnly, YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;
        if (batches == null) {
            return total;
        }
        YearMonth limit = month == null ? null : expiryLimit(month);
        for (Batch batch : batches) {
            if (batch == null || batch.qty() <= 0 || batch.rate() == null) {
                continue;
            }
            if (expiringOnly) {
                YearMonth expiry = yearMonth(batch.expiry());
                if (expiry.isBefore(month) || expiry.isAfter(limit)) {
                    continue;
                }
            }
            total = total.add(batch.rate().multiply(BigDecimal.valueOf(batch.qty())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
