package in.medmate.pharmacy_server.customer;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.phone.PhoneNumbers;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AddressRules {

    public static final int MAX_ADDRESSES = 10;
    public static final int NOTE_MAX = 140;

    private static final int LINE1_MAX = 120;
    private static final int LINE2_MAX = 160;
    private static final int LANDMARK_MAX = 120;
    private static final int CITY_MAX = 80;
    private static final int NAME_MAX = 80;
    private static final Pattern PINCODE = Pattern.compile("^560[0-9]{3}$");
    private static final Set<String> LABELS = Set.of("home", "work", "other");

    private AddressRules() {}

    public record Values(
            String label,
            String line1,
            String line2,
            String landmark,
            String city,
            String pincode,
            Double latitude,
            Double longitude,
            String receiverName,
            String receiverPhone,
            boolean callOnArrival,
            boolean leaveWithSecurity,
            boolean dontRingBell,
            boolean liftNotWorking,
            String riderNote,
            boolean selected) {}

    public record Prepared(Values values, String problem) {}

    public static Prepared prepareCreate(JsonNode body) {
        if (body == null || !body.isObject()) {
            return fail("Address details are required");
        }
        return prepare(body, null);
    }

    public static Prepared prepareUpdate(JsonNode body, Values current) {
        if (body == null || !body.isObject()) {
            return new Prepared(current, null);
        }
        return prepare(body, current);
    }

    public static boolean delivers(String pincode) {
        return pincode != null && PINCODE.matcher(pincode).matches();
    }

    private static Prepared prepare(JsonNode body, Values current) {
        boolean creating = current == null;
        String labelProblem = labelProblem(body, creating);
        if (labelProblem != null) {
            return fail(labelProblem);
        }
        String line1Problem = requiredProblem(body, "line1", creating, "Flat, house or building", LINE1_MAX);
        if (line1Problem != null) {
            return fail(line1Problem);
        }
        String line2Problem = requiredProblem(body, "line2", creating, "Area and street", LINE2_MAX);
        if (line2Problem != null) {
            return fail(line2Problem);
        }
        String landmarkProblem = optionalProblem(body, "landmark", LANDMARK_MAX, "Landmark");
        if (landmarkProblem != null) {
            return fail(landmarkProblem);
        }
        String cityProblem = requiredProblem(body, "city", creating, "City", CITY_MAX);
        if (cityProblem != null) {
            return fail(cityProblem);
        }
        String pincodeProblem = pincodeProblem(body, creating);
        if (pincodeProblem != null) {
            return fail(pincodeProblem);
        }
        String pinProblem = pinProblem(body, current);
        if (pinProblem != null) {
            return fail(pinProblem);
        }
        String nameProblem = requiredProblem(body, "receiverName", creating, "Name", NAME_MAX);
        if (nameProblem != null) {
            return fail(nameProblem);
        }
        String phoneProblem = phoneProblem(body, creating);
        if (phoneProblem != null) {
            return fail(phoneProblem);
        }
        String flags = flagProblem(body);
        if (flags != null) {
            return fail(flags);
        }
        String noteProblem = optionalProblem(body, "riderNote", NOTE_MAX, "Rider note");
        if (noteProblem != null) {
            return fail(noteProblem);
        }
        return new Prepared(
                new Values(
                        label(body, current),
                        required(body, "line1", current == null ? null : current.line1(), creating),
                        required(body, "line2", current == null ? null : current.line2(), creating),
                        optional(body, "landmark", current == null ? null : current.landmark()),
                        required(body, "city", current == null ? null : current.city(), creating),
                        pincode(body, current),
                        latitude(body, current),
                        longitude(body, current),
                        required(body, "receiverName", current == null ? null : current.receiverName(), creating),
                        phone(body, current),
                        flag(body, "callOnArrival", current != null && current.callOnArrival()),
                        flag(body, "leaveWithSecurity", current != null && current.leaveWithSecurity()),
                        flag(body, "dontRingBell", current != null && current.dontRingBell()),
                        flag(body, "liftNotWorking", current != null && current.liftNotWorking()),
                        optional(body, "riderNote", current == null ? null : current.riderNote()),
                        flag(body, "selected", current != null && current.selected())),
                null);
    }

    private static Prepared fail(String problem) {
        return new Prepared(null, problem);
    }

    private static String labelProblem(JsonNode body, boolean creating) {
        if (!creating && !present(body, "label")) {
            return null;
        }
        String value = text(body, "label").toLowerCase(Locale.ROOT);
        if (!LABELS.contains(value)) {
            return "Save as Home, Work, or Other";
        }
        return null;
    }

    private static String label(JsonNode body, Values current) {
        if (current != null && !present(body, "label")) {
            return current.label();
        }
        return text(body, "label").toLowerCase(Locale.ROOT);
    }

    private static String requiredProblem(JsonNode body, String field, boolean creating, String name, int max) {
        if (!creating && !present(body, field)) {
            return null;
        }
        String value = text(body, field);
        if (value.isEmpty()) {
            return name + " is required";
        }
        if (value.length() > max) {
            return name + " is too long";
        }
        return null;
    }

    private static String required(JsonNode body, String field, String current, boolean creating) {
        if (!creating && !present(body, field)) {
            return current;
        }
        return text(body, field);
    }

    private static String optionalProblem(JsonNode body, String field, int max, String name) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        String value = body.get(field).asText("").trim();
        if (value.length() > max) {
            return name + " is too long";
        }
        return null;
    }

    private static String optional(JsonNode body, String field, String current) {
        if (!body.has(field)) {
            return current;
        }
        if (body.get(field).isNull()) {
            return null;
        }
        String value = body.get(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String pincodeProblem(JsonNode body, boolean creating) {
        if (!creating && !present(body, "pincode")) {
            return null;
        }
        String value = text(body, "pincode").replace(" ", "");
        if (!PINCODE.matcher(value).matches()) {
            return "We deliver across Bengaluru (pincodes starting 560)";
        }
        return null;
    }

    private static String pincode(JsonNode body, Values current) {
        if (current != null && !present(body, "pincode")) {
            return current.pincode();
        }
        return text(body, "pincode").replace(" ", "");
    }

    private static String pinProblem(JsonNode body, Values current) {
        String latitudeProblem = numberProblem(body, "latitude", -90, 90, "Latitude");
        if (latitudeProblem != null) {
            return latitudeProblem;
        }
        String longitudeProblem = numberProblem(body, "longitude", -180, 180, "Longitude");
        if (longitudeProblem != null) {
            return longitudeProblem;
        }
        if (cleared(body, "latitude") != cleared(body, "longitude")) {
            return "A pin needs both latitude and longitude";
        }
        if (cleared(body, "latitude")) {
            return "Pin the delivery spot on the map";
        }
        Double latitude = latitude(body, current);
        Double longitude = longitude(body, current);
        if ((latitude == null) != (longitude == null)) {
            return "A pin needs both latitude and longitude";
        }
        if (latitude == null) {
            return "Pin the delivery spot on the map";
        }
        return null;
    }

    private static boolean cleared(JsonNode body, String field) {
        return body.has(field) && body.get(field).isNull();
    }

    private static String numberProblem(JsonNode body, String field, double min, double max, String name) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        JsonNode node = body.get(field);
        if (!node.isNumber()) {
            return name + " is out of range";
        }
        double value = node.asDouble();
        if (Double.isNaN(value) || value < min || value > max) {
            return name + " is out of range";
        }
        return null;
    }

    private static Double latitude(JsonNode body, Values current) {
        return coordinate(body, "latitude", current == null ? null : current.latitude());
    }

    private static Double longitude(JsonNode body, Values current) {
        return coordinate(body, "longitude", current == null ? null : current.longitude());
    }

    private static Double coordinate(JsonNode body, String field, Double current) {
        if (!body.has(field)) {
            return current;
        }
        if (body.get(field).isNull()) {
            return null;
        }
        return body.get(field).asDouble();
    }

    private static String phoneProblem(JsonNode body, boolean creating) {
        if (!creating && !present(body, "receiverPhone")) {
            return null;
        }
        if (PhoneNumbers.normalize(text(body, "receiverPhone")) == null) {
            return "Enter an Indian mobile number";
        }
        return null;
    }

    private static String phone(JsonNode body, Values current) {
        if (current != null && !present(body, "receiverPhone")) {
            return current.receiverPhone();
        }
        return PhoneNumbers.normalize(text(body, "receiverPhone"));
    }

    private static String flagProblem(JsonNode body) {
        for (String field :
                new String[] {"callOnArrival", "leaveWithSecurity", "dontRingBell", "liftNotWorking", "selected"}) {
            if (body.has(field) && !body.get(field).isNull() && !body.get(field).isBoolean()) {
                return "Flag is not recognised";
            }
        }
        return null;
    }

    private static boolean flag(JsonNode body, String field, boolean fallback) {
        if (!present(body, field)) {
            return fallback;
        }
        return body.get(field).asBoolean();
    }

    private static boolean present(JsonNode body, String field) {
        return body.has(field) && !body.get(field).isNull();
    }

    private static String text(JsonNode body, String field) {
        if (!present(body, field)) {
            return "";
        }
        return body.get(field).asText("").trim();
    }
}
