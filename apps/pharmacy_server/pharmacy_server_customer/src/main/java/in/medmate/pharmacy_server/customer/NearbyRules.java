package in.medmate.pharmacy_server.customer;

public final class NearbyRules {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    public static final double MAX_DISTANCE_KM = 40;

    private NearbyRules() {}

    public record Query(double latitude, double longitude, int limit) {}

    public static String problem(String latitude, String longitude, String limit) {
        String latitudeProblem = coordinate(latitude, -90, 90, "Latitude");
        if (latitudeProblem != null) {
            return latitudeProblem;
        }
        String longitudeProblem = coordinate(longitude, -180, 180, "Longitude");
        if (longitudeProblem != null) {
            return longitudeProblem;
        }
        return limitProblem(limit);
    }

    public static Query query(String latitude, String longitude, String limit) {
        int size = DEFAULT_LIMIT;
        if (limit != null && !limit.isBlank()) {
            size = Integer.parseInt(limit.trim());
            if (size > MAX_LIMIT) {
                size = MAX_LIMIT;
            }
        }
        return new Query(Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()), size);
    }

    private static String coordinate(String raw, double min, double max, String name) {
        if (raw == null || raw.isBlank()) {
            return name + " is required";
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < min || value > max) {
                return name + " is out of range";
            }
        } catch (NumberFormatException exception) {
            return name + " is out of range";
        }
        return null;
    }

    private static String limitProblem(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (Integer.parseInt(raw.trim()) < 1) {
                return "Limit is out of range";
            }
        } catch (NumberFormatException exception) {
            return "Limit is out of range";
        }
        return null;
    }
}
