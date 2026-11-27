package in.medmate.pharmacy_server.customer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;

public final class AppSectionRules {

    public static final int IMAGE_MAX = 2 * 1024 * 1024;

    private static final int CATEGORY_MAX = 80;
    private static final int POSITION_MAX = 10_000;
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");

    private AppSectionRules() {}

    public record Upload(String contentType, byte[] body) {}

    public record Draft(String category, int position) {}

    public record Prepared(Draft draft, String problem) {

        static Prepared problem(String detail) {
            return new Prepared(null, detail);
        }
    }

    public static Prepared prepare(JsonNode body) {
        if (body == null || !body.isObject()) {
            return Prepared.problem("Category is required");
        }
        String category = text(body, "category");
        if (category == null) {
            return Prepared.problem("Category is required");
        }
        if (category.length() > CATEGORY_MAX) {
            return Prepared.problem("Category is not valid");
        }
        Integer position = position(body);
        if (position == null || position > POSITION_MAX) {
            return Prepared.problem("Position is not valid");
        }
        return new Prepared(new Draft(category, position), null);
    }

    public static String imageProblem(Upload upload, boolean required) {
        if (upload == null || upload.body() == null || upload.body().length == 0) {
            return required ? "Image is required" : null;
        }
        if (!IMAGES.contains(media(upload.contentType()))) {
            return "Image must be an image";
        }
        if (upload.body().length > IMAGE_MAX) {
            return "Image is too large";
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

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer position(JsonNode body) {
        if (!body.has("position") || body.get("position").isNull()) {
            return 0;
        }
        JsonNode node = body.get("position");
        if (node.isIntegralNumber()) {
            long value = node.longValue();
            if (value < 0 || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }
        if (!node.isTextual()) {
            return null;
        }
        String text = node.asText().trim();
        if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.parseInt(text);
    }
}
