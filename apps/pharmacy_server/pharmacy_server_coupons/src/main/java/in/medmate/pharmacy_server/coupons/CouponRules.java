package in.medmate.pharmacy_server.coupons;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CouponRules {

    public static final int BANNER_MAX = 2 * 1024 * 1024;

    private static final int TITLE_MAX = 80;
    private static final int USE_MAX = 1_000_000;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9-]{4,20}");
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CouponRules() {}

    public record Upload(String contentType, byte[] body) {}

    public record Draft(
            String code,
            String title,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscount,
            BigDecimal minOrder,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String pharmacyScope,
            List<UUID> pharmacyIds,
            boolean active) {}

    public record Prepared(Draft draft, String problem) {

        static Prepared problem(String detail) {
            return new Prepared(null, detail);
        }
    }

    public record Snapshot(
            String code,
            String title,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscount,
            BigDecimal minOrder,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            String pharmacyScope,
            List<UUID> pharmacyIds,
            boolean active) {}

    public record Quote(BigDecimal orderTotal, BigDecimal discount, BigDecimal payable, String problem) {

        static Quote problem(String detail) {
            return new Quote(null, null, null, detail);
        }
    }

    public record PreviewRequest(String code, BigDecimal orderTotal, UUID pharmacyId, String problem) {

        static PreviewRequest problem(String detail) {
            return new PreviewRequest(null, null, null, detail);
        }
    }

    public static Prepared prepareCreate(JsonNode body) {
        return parse(body, null);
    }

    public static Prepared prepareUpdate(JsonNode body, Draft current) {
        return parse(body, current);
    }

    public static String bannerProblem(Upload upload, boolean required) {
        if (upload == null || upload.body() == null || upload.body().length == 0) {
            return required ? "Banner is required" : null;
        }
        if (!IMAGES.contains(media(upload.contentType()))) {
            return "Banner must be an image";
        }
        if (upload.body().length > BANNER_MAX) {
            return "Banner is too large";
        }
        return null;
    }

    public static String media(String contentType) {
        if (contentType == null) {
            return "";
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(base)) {
            return "image/jpeg";
        }
        return base;
    }

    public static PreviewRequest previewRequest(JsonNode body) {
        if (body == null || !body.isObject()) {
            return PreviewRequest.problem("Coupon code is required");
        }
        String code = code(body);
        if (code == null) {
            return PreviewRequest.problem(codeProblem(body));
        }
        Money orderTotal = money(body, "orderTotal", "Order total");
        if (orderTotal.problem() != null) {
            return PreviewRequest.problem(orderTotal.problem());
        }
        if (orderTotal.value() == null || orderTotal.value().compareTo(BigDecimal.ZERO) <= 0) {
            return PreviewRequest.problem("Order total must be greater than zero");
        }
        UUID pharmacyId = null;
        if (present(body, "pharmacyId")) {
            pharmacyId = uuid(text(body, "pharmacyId"));
            if (pharmacyId == null) {
                return PreviewRequest.problem("Pharmacy is not valid");
            }
        }
        return new PreviewRequest(code, orderTotal.value(), pharmacyId, null);
    }

    public static Quote quote(
            Snapshot coupon, int totalUses, int customerUses, BigDecimal orderTotal, UUID pharmacyId, Instant now) {
        if (!coupon.active()) {
            return Quote.problem("Coupon is not active");
        }
        if (coupon.startsAt() != null && now.isBefore(coupon.startsAt())) {
            return Quote.problem("Coupon has not started");
        }
        if (coupon.endsAt() != null && !now.isBefore(coupon.endsAt())) {
            return Quote.problem("Coupon has expired");
        }
        if ("selected".equals(coupon.pharmacyScope())) {
            if (pharmacyId == null) {
                return Quote.problem("Choose a pharmacy");
            }
            if (coupon.pharmacyIds() == null || !coupon.pharmacyIds().contains(pharmacyId)) {
                return Quote.problem("Coupon does not apply at this pharmacy");
            }
        }
        if (coupon.maxRedemptions() != null && totalUses >= coupon.maxRedemptions()) {
            return Quote.problem("Coupon has reached its use limit");
        }
        if (coupon.maxPerCustomer() != null && customerUses >= coupon.maxPerCustomer()) {
            return Quote.problem("You have already used this coupon");
        }
        BigDecimal total = orderTotal.setScale(2, RoundingMode.HALF_UP);
        if (coupon.minOrder() != null && total.compareTo(coupon.minOrder()) < 0) {
            return Quote.problem("Order total is below the minimum");
        }
        BigDecimal discount = discount(coupon, total);
        BigDecimal payable = total.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new Quote(total, discount, payable, null);
    }

    private static BigDecimal discount(Snapshot coupon, BigDecimal orderTotal) {
        BigDecimal discount;
        if ("pct".equals(coupon.discountType())) {
            discount = orderTotal.multiply(coupon.discountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (coupon.maxDiscount() != null && discount.compareTo(coupon.maxDiscount()) > 0) {
                discount = coupon.maxDiscount().setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            discount = coupon.discountValue().setScale(2, RoundingMode.HALF_UP);
        }
        if (discount.compareTo(orderTotal) > 0) {
            return orderTotal;
        }
        return discount;
    }

    private static Prepared parse(JsonNode body, Draft current) {
        if (body == null || !body.isObject()) {
            return Prepared.problem("Title is required");
        }
        String code = resolveCode(body, current);
        if (code == null) {
            return Prepared.problem(current == null ? codeProblem(body) : "Coupon code cannot be changed");
        }
        String title = requiredText(body, "title");
        if (title == null) {
            return Prepared.problem("Title is required");
        }
        if (title.length() > TITLE_MAX) {
            return Prepared.problem("Title is too long");
        }
        String type = choice(body, "discountType");
        if (type == null) {
            return Prepared.problem("Discount type is not recognised");
        }
        Money value = money(body, "discountValue", "Discount");
        if (value.problem() != null) {
            return Prepared.problem(value.problem());
        }
        if (value.value() == null) {
            return Prepared.problem("Discount must be greater than zero");
        }
        if ("pct".equals(type) && value.value().compareTo(HUNDRED) > 0) {
            return Prepared.problem("Percent discount cannot exceed 100");
        }
        Money maxDiscount = optionalMoney(body, "maxDiscount", "Maximum discount");
        if (maxDiscount.problem() != null) {
            return Prepared.problem(maxDiscount.problem());
        }
        if (maxDiscount.value() != null && !"pct".equals(type)) {
            return Prepared.problem("Maximum discount is only for a percent coupon");
        }
        Money minOrder = optionalMoney(body, "minOrder", "Minimum order");
        if (minOrder.problem() != null) {
            return Prepared.problem(minOrder.problem());
        }
        Instant startsAt = instant(body, "startsAt");
        if (startsAt == null && dateProblem(body, "startsAt") != null) {
            return Prepared.problem("Start is invalid");
        }
        Instant endsAt = instant(body, "endsAt");
        if (endsAt == null && dateProblem(body, "endsAt") != null) {
            return Prepared.problem("End is invalid");
        }
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            return Prepared.problem("End must be after the start");
        }
        Whole uses = whole(body, "maxRedemptions", "Use limit");
        if (uses.problem() != null) {
            return Prepared.problem(uses.problem());
        }
        Whole perCustomer = whole(body, "maxPerCustomer", "Per-customer limit");
        if (perCustomer.problem() != null) {
            return Prepared.problem(perCustomer.problem());
        }
        String scope = scope(body);
        if (scope == null) {
            return Prepared.problem("Pharmacy scope is not recognised");
        }
        ParsedIds pharmacies = pharmacyIds(body);
        if (pharmacies.problem() != null) {
            return Prepared.problem(pharmacies.problem());
        }
        List<UUID> ids = "selected".equals(scope) ? pharmacies.ids() : List.of();
        if ("selected".equals(scope) && ids.isEmpty()) {
            return Prepared.problem("Choose at least one pharmacy");
        }
        Boolean active = flag(body, "active");
        if (present(body, "active") && active == null) {
            return Prepared.problem("Active is not valid");
        }
        return new Prepared(
                new Draft(
                        code,
                        title,
                        type,
                        value.value(),
                        maxDiscount.value(),
                        minOrder.value(),
                        startsAt,
                        endsAt,
                        uses.value(),
                        perCustomer.value(),
                        scope,
                        ids,
                        active == null || active),
                null);
    }

    private static String resolveCode(JsonNode body, Draft current) {
        if (current == null) {
            return code(body);
        }
        if (!present(body, "code")) {
            return current.code();
        }
        String incoming = code(body);
        if (incoming == null || !incoming.equals(current.code())) {
            return null;
        }
        return current.code();
    }

    private static String code(JsonNode body) {
        String value = requiredText(body, "code");
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private static String codeProblem(JsonNode body) {
        String value = requiredText(body, "code");
        if (value == null) {
            return "Coupon code is required";
        }
        return "Coupon code is not valid";
    }

    private static String choice(JsonNode body, String field) {
        if (!present(body, field)) {
            return null;
        }
        String value = text(body, field);
        if ("pct".equals(value) || "flat".equals(value)) {
            return value;
        }
        return null;
    }

    private static String scope(JsonNode body) {
        if (!present(body, "pharmacyScope")) {
            return "all";
        }
        String value = text(body, "pharmacyScope");
        if ("all".equals(value) || "selected".equals(value)) {
            return value;
        }
        return null;
    }

    private static ParsedIds pharmacyIds(JsonNode body) {
        if (!present(body, "pharmacyIds")) {
            return new ParsedIds(List.of(), null);
        }
        JsonNode node = body.get("pharmacyIds");
        if (!node.isArray()) {
            return new ParsedIds(List.of(), "Pharmacy is not valid");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (JsonNode item : node) {
            UUID id = uuid(item.asText("").trim());
            if (id == null) {
                return new ParsedIds(List.of(), "Pharmacy is not valid");
            }
            ids.add(id);
        }
        return new ParsedIds(List.copyOf(ids), null);
    }

    private static UUID uuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Money(BigDecimal value, String problem) {}

    private static Money money(JsonNode body, String field, String label) {
        if (!present(body, field)) {
            return new Money(null, label + " is invalid");
        }
        return parsedMoney(body.get(field), label);
    }

    private static Money optionalMoney(JsonNode body, String field, String label) {
        if (!present(body, field)) {
            return new Money(null, null);
        }
        String raw = body.get(field).isTextual() ? body.get(field).asText().trim() : null;
        if (raw != null && raw.isEmpty()) {
            return new Money(null, null);
        }
        return parsedMoney(body.get(field), label);
    }

    private static Money parsedMoney(JsonNode node, String label) {
        BigDecimal value = decimal(node);
        if (value == null) {
            return new Money(null, label + " is invalid");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            return new Money(null, label + " is invalid");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return new Money(null, label + " must be greater than zero");
        }
        if (value.precision() - value.scale() > 10) {
            return new Money(null, label + " is invalid");
        }
        return new Money(value.setScale(2, RoundingMode.UNNECESSARY), null);
    }

    private static BigDecimal decimal(JsonNode node) {
        try {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isNumber()) {
                return node.decimalValue();
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

    private record Whole(Integer value, String problem) {}

    private static Whole whole(JsonNode body, String field, String label) {
        if (!present(body, field)) {
            return new Whole(null, null);
        }
        JsonNode node = body.get(field);
        if (node.isTextual() && node.asText().trim().isEmpty()) {
            return new Whole(null, null);
        }
        Integer value = integer(node);
        if (value == null || value <= 0 || value > USE_MAX) {
            return new Whole(null, label + " is invalid");
        }
        return new Whole(value, null);
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            long value = node.longValue();
            if (value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }
        if (node.isNumber()) {
            return null;
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant instant(JsonNode body, String field) {
        if (dateProblem(body, field) != null || !present(body, field)) {
            return null;
        }
        String raw = text(body, field);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String dateProblem(JsonNode body, String field) {
        if (!present(body, field)) {
            return null;
        }
        String raw = text(body, field);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            Instant.parse(raw);
            return null;
        } catch (DateTimeParseException exception) {
            return "invalid";
        }
    }

    private static Boolean flag(JsonNode body, String field) {
        if (!present(body, field) || !body.get(field).isBoolean()) {
            return null;
        }
        return body.get(field).booleanValue();
    }

    private static String requiredText(JsonNode body, String field) {
        if (!present(body, field)) {
            return null;
        }
        String value = text(body, field);
        return value.isEmpty() ? null : value;
    }

    private static boolean present(JsonNode body, String field) {
        return body != null && body.has(field) && !body.get(field).isNull();
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText().trim();
    }

    private record ParsedIds(List<UUID> ids, String problem) {}
}
