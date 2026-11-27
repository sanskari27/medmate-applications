package in.medmate.pharmacy_server.customer;

import in.medmate.pharmacy_server.identity.pharmacy.PharmacyRules;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class NearbyRules {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    public static final double MAX_DISTANCE_KM = 40;
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final HoursNow CLOSED = new HoursNow(true, null);

    private NearbyRules() {}

    public record Query(double latitude, double longitude, int limit) {}

    public record HoursNow(boolean closed, String closesOn) {}

    public static HoursNow today(List<PharmacyRules.OpeningHour> hours, Instant now) {
        if (hours == null || hours.isEmpty() || now == null) {
            return CLOSED;
        }
        ZonedDateTime local = now.atZone(ZONE);
        String day = local.getDayOfWeek().name().toLowerCase(Locale.ROOT);
        LocalTime time = local.toLocalTime();
        for (PharmacyRules.OpeningHour hour : hours) {
            if (hour == null
                    || hour.day() == null
                    || !day.equals(hour.day().toLowerCase(Locale.ROOT))
                    || hour.closed()) {
                continue;
            }
            LocalTime open = clock(hour.opensAt());
            LocalTime close = clock(hour.closesAt());
            if (open == null || close == null || !open.isBefore(close)) {
                continue;
            }
            if (!time.isBefore(open) && time.isBefore(close)) {
                return new HoursNow(false, hour.closesAt());
            }
        }
        return CLOSED;
    }

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

    private static LocalTime clock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
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
