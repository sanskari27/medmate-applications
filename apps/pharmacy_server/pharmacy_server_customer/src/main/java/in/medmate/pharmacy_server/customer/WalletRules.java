package in.medmate.pharmacy_server.customer;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

public final class WalletRules {

    public static final int ORDER_CREDITS = 15;
    public static final int REFERRAL_CREDITS = 100;
    public static final String ORDER = "order";
    public static final String REFERRAL = "referral";
    public static final String PAYMENT = "payment";

    private static final int CODE_LENGTH = 8;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    private static final Pattern CODE = Pattern.compile("^[" + ALPHABET + "]{8}$");

    private WalletRules() {}

    public record PreparedCode(String code, String problem) {}

    public record Settlement(int balance, int applied, BigDecimal due, String problem) {}

    public static String code(Random random) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public static PreparedCode prepareCode(JsonNode body) {
        if (body == null
                || !body.isObject()
                || !body.has("code")
                || body.get("code").isNull()) {
            return new PreparedCode(null, "Enter a referral code");
        }
        String raw = body.get("code").asText("").trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(raw).matches()) {
            return new PreparedCode(null, "Enter a referral code");
        }
        return new PreparedCode(raw, null);
    }

    public static String linkProblem(boolean unknown, boolean self, boolean already, boolean ordered, boolean mutual) {
        if (unknown) {
            return "Referral code was not found";
        }
        if (self) {
            return "You cannot use your own referral code";
        }
        if (already) {
            return "A referral code is already saved";
        }
        if (ordered) {
            return "Referral codes can only be used before your first order";
        }
        if (mutual) {
            return "You cannot use each other's referral codes";
        }
        return null;
    }

    public static BigDecimal payable(JsonNode body) {
        if (body == null
                || !body.isObject()
                || !body.has("payable")
                || body.get("payable").isNull()) {
            return null;
        }
        return decimal(body.get("payable"));
    }

    public static String payableProblem(BigDecimal payable) {
        if (payable == null) {
            return "Payable is invalid";
        }
        BigDecimal stripped = payable.stripTrailingZeros();
        if (stripped.scale() > 2) {
            return "Payable is invalid";
        }
        if (stripped.compareTo(BigDecimal.ZERO) <= 0) {
            return "Payable must be greater than zero";
        }
        if (stripped.precision() - stripped.scale() > 10) {
            return "Payable is invalid";
        }
        if (stripped.setScale(0, RoundingMode.DOWN).compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return "Payable is invalid";
        }
        return null;
    }

    public static Settlement quote(int balance, BigDecimal payable) {
        String problem = payableProblem(payable);
        if (problem != null) {
            return new Settlement(balance, 0, null, problem);
        }
        BigDecimal total = rupees(payable);
        int whole = total.setScale(0, RoundingMode.DOWN).intValueExact();
        int applied = Math.min(Math.max(balance, 0), whole);
        BigDecimal due = total.subtract(BigDecimal.valueOf(applied)).setScale(2, RoundingMode.UNNECESSARY);
        return new Settlement(balance, applied, due, null);
    }

    public static BigDecimal due(BigDecimal payable, int applied) {
        BigDecimal total = rupees(payable);
        BigDecimal remaining = total.subtract(BigDecimal.valueOf(applied));
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        return remaining.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static BigDecimal rupees(BigDecimal payable) {
        BigDecimal stripped = payable.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal decimal(JsonNode node) {
        try {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (!node.isTextual()) {
                return null;
            }
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            return new BigDecimal(text);
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }
}
