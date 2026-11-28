package in.medmate.pharmacy_server.customer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class AppSectionRules {

    public static final int IMAGE_MAX = 2 * 1024 * 1024;
    public static final int CATEGORY_LIMIT = 12;
    public static final int IMAGE_LIMIT = 6;

    private static final int NAME_MAX = 80;
    private static final int POSITION_MAX = 10_000;
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");

    private AppSectionRules() {}

    public record Upload(String contentType, byte[] body) {}

    public record ImageSlot(UUID id) {}

    public record CategoryDraft(String category, List<ImageSlot> images) {}

    public record Draft(String title, int position, List<CategoryDraft> categories) {

        public int newImages() {
            int count = 0;
            for (CategoryDraft category : categories) {
                for (ImageSlot image : category.images()) {
                    if (image.id() == null) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    public record Prepared(Draft draft, String problem) {

        static Prepared problem(String detail) {
            return new Prepared(null, detail);
        }
    }

    public static Prepared prepare(JsonNode body) {
        if (body == null || !body.isObject()) {
            return Prepared.problem("Name is required");
        }
        String title = text(body, "title");
        if (title == null) {
            return Prepared.problem("Name is required");
        }
        if (title.length() > NAME_MAX) {
            return Prepared.problem("Name is not valid");
        }
        Integer position = position(body);
        if (position == null || position > POSITION_MAX) {
            return Prepared.problem("Position is not valid");
        }
        JsonNode categories = body.get("categories");
        if (categories == null || !categories.isArray() || categories.isEmpty()) {
            return Prepared.problem("Add a category");
        }
        if (categories.size() > CATEGORY_LIMIT) {
            return Prepared.problem("Too many categories");
        }
        List<CategoryDraft> parsed = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<UUID> imageIds = new HashSet<>();
        for (JsonNode category : categories) {
            ParsedCategory parsedCategory = category(category, names, imageIds);
            if (parsedCategory.problem() != null) {
                return Prepared.problem(parsedCategory.problem());
            }
            parsed.add(parsedCategory.category());
        }
        return new Prepared(new Draft(title, position, List.copyOf(parsed)), null);
    }

    private record ParsedCategory(CategoryDraft category, String problem) {

        static ParsedCategory problem(String detail) {
            return new ParsedCategory(null, detail);
        }
    }

    private record ParsedSlot(ImageSlot slot, String problem) {

        static ParsedSlot problem(String detail) {
            return new ParsedSlot(null, detail);
        }
    }

    private static ParsedCategory category(JsonNode node, Set<String> names, Set<UUID> imageIds) {
        if (node == null || !node.isObject()) {
            return ParsedCategory.problem("Category is required");
        }
        String name = text(node, "category");
        if (name == null) {
            return ParsedCategory.problem("Category is required");
        }
        if (name.length() > NAME_MAX) {
            return ParsedCategory.problem("Category is not valid");
        }
        if (!names.add(name.toLowerCase(Locale.ROOT))) {
            return ParsedCategory.problem("Category is already listed");
        }
        JsonNode images = node.get("images");
        if (images == null || !images.isArray() || images.isEmpty()) {
            return ParsedCategory.problem("Each category needs an image");
        }
        if (images.size() > IMAGE_LIMIT) {
            return ParsedCategory.problem("Too many category images");
        }
        List<ImageSlot> slots = new ArrayList<>();
        for (JsonNode image : images) {
            ParsedSlot slot = slot(image, imageIds);
            if (slot.problem() != null) {
                return ParsedCategory.problem(slot.problem());
            }
            slots.add(slot.slot());
        }
        return new ParsedCategory(new CategoryDraft(name, List.copyOf(slots)), null);
    }

    private static ParsedSlot slot(JsonNode node, Set<UUID> imageIds) {
        if (node == null || !node.isObject()) {
            return ParsedSlot.problem("Category image is not valid");
        }
        boolean file = node.has("file")
                && node.get("file").isBoolean()
                && node.get("file").booleanValue();
        boolean hasId = node.has("id") && !node.get("id").isNull();
        if (file == hasId) {
            return ParsedSlot.problem("Category image is not valid");
        }
        if (file) {
            return new ParsedSlot(new ImageSlot(null), null);
        }
        if (!node.get("id").isTextual()) {
            return ParsedSlot.problem("Category image is not valid");
        }
        UUID id;
        try {
            id = UUID.fromString(node.get("id").asText().trim());
        } catch (IllegalArgumentException exception) {
            return ParsedSlot.problem("Category image is not valid");
        }
        if (!imageIds.add(id)) {
            return ParsedSlot.problem("Category image is not valid");
        }
        return new ParsedSlot(new ImageSlot(id), null);
    }

    public static String imageProblem(Upload upload, boolean required) {
        return fileProblem(upload, required, "Image");
    }

    public static String fileProblem(Upload upload, boolean required, String name) {
        if (upload == null || upload.body() == null || upload.body().length == 0) {
            return required ? name + " is required" : null;
        }
        if (!IMAGES.contains(media(upload.contentType()))) {
            return name + " must be an image";
        }
        if (upload.body().length > IMAGE_MAX) {
            return name + " is too large";
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
        if (node == null || node.isNull() || !node.isTextual()) {
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
