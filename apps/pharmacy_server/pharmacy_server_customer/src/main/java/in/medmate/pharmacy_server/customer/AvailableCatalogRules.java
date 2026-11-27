package in.medmate.pharmacy_server.customer;

public final class AvailableCatalogRules {

    private AvailableCatalogRules() {}

    public record Browse(String category, String query, String problem) {}

    public static Browse browse(String rawCategory, String rawQuery) {
        String category = rawCategory == null ? "" : rawCategory.trim();
        if (category.length() > 80) {
            return new Browse(null, null, "Category is not valid");
        }
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (!query.isEmpty() && (query.length() < 2 || query.length() > 80)) {
            return new Browse(null, null, "Query is not valid");
        }
        return new Browse(category, query, null);
    }
}
