package in.medmate.pharmacy_server.care;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.phone.PhoneNumbers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CareRules {

    public static final int LOW_STOCK_DAYS = 7;
    public static final int NOTE_MAX = 120;
    public static final int NAME_MAX = 80;
    public static final int MEDICINE_NAME_MAX = 120;

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("1000");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final Set<String> RELATIONS = Set.of(
            "self",
            "mother",
            "father",
            "spouse",
            "son",
            "daughter",
            "brother",
            "sister",
            "grandmother",
            "grandfather",
            "other");
    private static final Set<String> FAMILY_RELATIONS = Set.of(
            "mother",
            "father",
            "spouse",
            "son",
            "daughter",
            "brother",
            "sister",
            "grandmother",
            "grandfather",
            "other");
    private static final Set<String> CONDITIONS =
            Set.of("diabetes", "high_bp", "thyroid", "asthma", "heart", "cholesterol", "arthritis");
    private static final Set<String> ALLERGIES = Set.of("penicillin", "sulfa", "aspirin", "painkillers");
    private static final Set<String> UNITS = Set.of("tablet", "capsule", "ml");
    private static final Set<String> FOODS = Set.of("before", "after", "any");
    private static final List<String> SLOTS = List.of("morning", "afternoon", "evening", "night");

    private CareRules() {}

    public record MemberValues(
            String displayName,
            String relation,
            Integer ageYears,
            LocalDate birthday,
            List<String> conditions,
            List<String> allergies,
            String mobile,
            boolean consent) {}

    public record MedicineValues(
            UUID memberId,
            String productId,
            String name,
            BigDecimal doseAmount,
            String doseUnit,
            String food,
            String weekdays,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal stockUnits,
            boolean reminders,
            String note,
            List<LocalTime> times) {}

    public record ScheduleLine(
            UUID lineId,
            BigDecimal doseAmount,
            String doseUnit,
            String food,
            String weekdays,
            int days,
            List<LocalTime> times,
            BigDecimal stockUnits) {}

    public record ScheduleRequest(UUID memberId, List<ScheduleLine> lines) {}

    public record Parsed<T>(T value, String problem) {}

    public record Transition(String status, BigDecimal stockDelta) {}

    public record MedicineSnap(
            UUID id,
            UUID memberId,
            String productId,
            String name,
            BigDecimal doseAmount,
            String doseUnit,
            String food,
            String weekdays,
            LocalDate startsOn,
            LocalDate endsOn,
            BigDecimal stockUnits,
            BigDecimal unitsPerDose,
            List<LocalTime> times) {}

    public record DoseSnap(UUID id, UUID medicineId, LocalDate date, LocalTime time, String status) {}

    public record BoardDose(
            UUID id,
            UUID medicineId,
            String name,
            String detail,
            String time,
            String timeLabel,
            String status,
            boolean due) {}

    public record BoardGroup(String slot, int taken, int total, List<BoardDose> doses) {}

    public record BoardDay(
            LocalDate date, String weekday, int day, boolean complete, boolean today, boolean selected, int percent) {}

    public record BoardProgress(int taken, int total, int percent, String next) {}

    public record LowStock(UUID medicineId, String productId, String name, BigDecimal stockUnits, int supplyDays) {}

    public record BoardMedicine(UUID id, String name, String summary, String stockLine, String productId) {}

    public record Board(
            BoardProgress progress,
            List<BoardDay> week,
            LowStock lowStock,
            List<BoardGroup> groups,
            List<BoardMedicine> medicines) {}

    public static String selfName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "You";
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > NAME_MAX) {
            return trimmed.substring(0, NAME_MAX);
        }
        return trimmed;
    }

    public static MemberValues selfMember(String displayName) {
        return new MemberValues(selfName(displayName), "self", null, null, List.of(), List.of(), null, true);
    }

    public static Parsed<MemberValues> parseMember(JsonNode body, MemberValues current) {
        if (creating(current) && (body == null || !body.isObject())) {
            return fail("Family member details are required");
        }
        if (body == null || !body.isObject()) {
            return ok(current);
        }
        boolean creating = creating(current);
        String relation = relation(body, current, creating);
        if (relation == null) {
            return fail(creating ? "Choose a family relation" : "The account holder stays on the schedule");
        }
        String name = text(body, "displayName", current == null ? null : current.displayName(), creating);
        if (name == null || name.isBlank()) {
            return fail("Name is required");
        }
        if (name.length() > NAME_MAX) {
            return fail("Name must be 80 characters or fewer");
        }
        Integer age = integer(body, "ageYears", current == null ? null : current.ageYears());
        if (age != null && age.intValue() == Integer.MIN_VALUE) {
            return fail("Age must be between 0 and 120");
        }
        LocalDate birthday = date(body, "birthday", current == null ? null : current.birthday());
        if (birthday == LocalDate.MIN) {
            return fail("Birthday is not valid");
        }
        if (age != null && birthday != null) {
            return fail("Give an age or a birthday, not both");
        }
        if (!"self".equals(relation) && age == null && birthday == null) {
            return fail("Age or birthday is required");
        }
        if (age != null && (age < 0 || age > 120)) {
            return fail("Age must be between 0 and 120");
        }
        List<String> conditions =
                tags(body, "conditions", CONDITIONS, current == null ? List.of() : current.conditions());
        if (conditions == null) {
            return fail("Choose a condition from the list");
        }
        List<String> allergies = tags(body, "allergies", ALLERGIES, current == null ? List.of() : current.allergies());
        if (allergies == null) {
            return fail("Choose an allergy from the list");
        }
        String mobile = optionalText(body, "mobile", current == null ? null : current.mobile());
        if (mobile == null && body.has("mobile") && !body.get("mobile").isNull()) {
            return fail("Mobile number must be an Indian mobile");
        }
        if (mobile != null && mobile.isBlank()) {
            mobile = null;
        }
        if (mobile != null) {
            mobile = PhoneNumbers.normalize(mobile);
            if (mobile == null) {
                return fail("Mobile number must be an Indian mobile");
            }
        }
        boolean consent = "self".equals(relation) || flag(body, "consent", current != null && current.consent());
        if (!"self".equals(relation) && !consent) {
            return fail("Add this person only with their permission");
        }
        return ok(new MemberValues(name, relation, age, birthday, conditions, allergies, mobile, consent));
    }

    public static Parsed<MedicineValues> parseMedicine(JsonNode body, MedicineValues current) {
        if (creating(current) && (body == null || !body.isObject())) {
            return fail("Medicine details are required");
        }
        if (body == null || !body.isObject()) {
            return ok(current);
        }
        boolean creating = creating(current);
        UUID memberId = uuid(body, "memberId", current == null ? null : current.memberId());
        if (memberId == null) {
            return fail("Choose who this medicine is for");
        }
        String productId = optionalText(body, "productId", current == null ? null : current.productId());
        if (productId != null && productId.isBlank()) {
            productId = null;
        }
        if (productId == null
                && body.has("productId")
                && body.get("productId").isTextual() == false
                && !body.get("productId").isNull()) {
            return fail("Medicine was not found in the catalogue");
        }
        String name = text(body, "name", current == null ? null : current.name(), creating);
        if (name == null || name.isBlank()) {
            return fail("Medicine name is required");
        }
        if (name.length() > MEDICINE_NAME_MAX) {
            return fail("Medicine name must be 120 characters or fewer");
        }
        BigDecimal amount = decimal(body, "doseAmount", current == null ? null : current.doseAmount());
        if (!amountOk(amount)) {
            return fail("Choose a dose");
        }
        String unit = choice(body, "doseUnit", UNITS, current == null ? null : current.doseUnit(), creating);
        if (unit == null) {
            return fail("Choose a dose");
        }
        String food = choice(body, "food", FOODS, current == null ? null : current.food(), creating);
        if (food == null) {
            return fail("Choose when the dose is taken");
        }
        String weekdays = weekdays(body, current == null ? null : current.weekdays(), creating);
        if (weekdays == null) {
            return fail("Choose at least one day");
        }
        LocalDate starts = date(body, "startsOn", current == null ? null : current.startsOn());
        if (starts == null || starts == LocalDate.MIN) {
            return fail(starts == LocalDate.MIN ? "Start date is not valid" : "Start date is required");
        }
        LocalDate ends = date(body, "endsOn", current == null ? null : current.endsOn());
        if (ends == LocalDate.MIN) {
            return fail("End date is not valid");
        }
        if (ends != null && ends.isBefore(starts)) {
            return fail("End date is before the start");
        }
        BigDecimal stock = decimal(body, "stockUnits", current == null ? null : current.stockUnits());
        if (stock == null || stock.signum() < 0 || stock.compareTo(AMOUNT_MAX) > 0) {
            return fail("Stock cannot be negative");
        }
        boolean reminders = flag(body, "reminders", current == null || current.reminders());
        String note = optionalText(body, "note", current == null ? null : current.note());
        if (note != null && note.length() > NOTE_MAX) {
            return fail("Note must be 120 characters or fewer");
        }
        if (note != null && note.isBlank()) {
            note = null;
        }
        List<LocalTime> times = times(body, current == null ? List.of() : current.times(), creating);
        if (times == null) {
            return fail("Add a time");
        }
        if (times.isEmpty()) {
            return fail("Add a time");
        }
        if (times.size() > 6) {
            return fail("A medicine can have at most 6 times");
        }
        return ok(new MedicineValues(
                memberId, productId, name, amount, unit, food, weekdays, starts, ends, stock, reminders, note, times));
    }

    public static Parsed<ScheduleRequest> parseSchedule(JsonNode body) {
        if (body == null || !body.isObject()) {
            return fail("Choose at least one medicine");
        }
        UUID memberId = uuid(body, "memberId", null);
        if (memberId == null) {
            return fail("Choose who these medicines are for");
        }
        JsonNode lines = body.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            return fail("Choose at least one medicine");
        }
        List<ScheduleLine> parsed = new ArrayList<>();
        for (JsonNode line : lines) {
            Parsed<ScheduleLine> item = parseScheduleLine(line);
            if (item.problem() != null) {
                return fail(item.problem());
            }
            parsed.add(item.value());
        }
        return ok(new ScheduleRequest(memberId, parsed));
    }

    public static Parsed<ScheduleLine> parseScheduleLine(JsonNode line) {
        if (line == null || !line.isObject()) {
            return fail("Choose at least one medicine");
        }
        UUID lineId = uuid(line, "lineId", null);
        if (lineId == null) {
            return fail("Choose at least one medicine");
        }
        BigDecimal amount = decimal(line, "doseAmount", null);
        if (!amountOk(amount)) {
            return fail("Choose a dose");
        }
        String unit = choice(line, "doseUnit", UNITS, null, true);
        if (unit == null) {
            return fail("Choose a dose");
        }
        String food = choice(line, "food", FOODS, null, true);
        if (food == null) {
            return fail("Choose when the dose is taken");
        }
        String weekdays = weekdays(line, null, true);
        if (weekdays == null) {
            return fail("Choose at least one day");
        }
        BigDecimal stock = decimal(line, "stockUnits", null);
        if (stock == null || stock.signum() < 0 || stock.compareTo(AMOUNT_MAX) > 0) {
            return fail("Stock cannot be negative");
        }
        List<LocalTime> times = times(line, List.of(), true);
        if (times == null || times.isEmpty()) {
            return fail("Add a time");
        }
        if (times.size() > 6) {
            return fail("A medicine can have at most 6 times");
        }
        JsonNode daysNode = line.get("days");
        if (daysNode == null || !daysNode.isIntegralNumber()) {
            return fail("Days must be between 1 and 365");
        }
        int days = daysNode.asInt();
        if (days < 1 || days > 365) {
            return fail("Days must be between 1 and 365");
        }
        return ok(new ScheduleLine(lineId, amount, unit, food, weekdays, days, times, stock));
    }

    public static Transition transition(String current, String action, BigDecimal units) {
        if ("pending".equals(current) && "taken".equals(action)) {
            return new Transition("taken", units.negate());
        }
        if ("pending".equals(current) && "skipped".equals(action)) {
            return new Transition("skipped", BigDecimal.ZERO);
        }
        if ("taken".equals(current) && "pending".equals(action)) {
            return new Transition("pending", units);
        }
        if ("skipped".equals(current) && "pending".equals(action)) {
            return new Transition("pending", BigDecimal.ZERO);
        }
        return null;
    }

    public static String slot(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        if (minutes >= 5 * 60 && minutes < 12 * 60) {
            return "morning";
        }
        if (minutes >= 12 * 60 && minutes < 17 * 60) {
            return "afternoon";
        }
        if (minutes >= 17 * 60 && minutes < 22 * 60) {
            return "evening";
        }
        return "night";
    }

    public static int supplyDays(BigDecimal stock, BigDecimal unitsPerDose, String weekdays, int timesPerDay) {
        if (stock == null || unitsPerDose == null || unitsPerDose.signum() <= 0 || timesPerDay <= 0) {
            return 0;
        }
        int dosesLeft = stock.divide(unitsPerDose, 0, RoundingMode.FLOOR).intValue();
        int daysOn = 0;
        for (int index = 0; index < weekdays.length(); index++) {
            if (weekdays.charAt(index) == '1') {
                daysOn++;
            }
        }
        int perWeek = daysOn * timesPerDay;
        if (perWeek == 0) {
            return 0;
        }
        return dosesLeft * 7 / perWeek;
    }

    public static boolean activeOn(LocalDate day, LocalDate starts, LocalDate ends, String weekdays) {
        if (day.isBefore(starts)) {
            return false;
        }
        if (ends != null && day.isAfter(ends)) {
            return false;
        }
        return weekdays.charAt(day.getDayOfWeek().getValue() - 1) == '1';
    }

    public static List<LocalDate> window(LocalDate today) {
        List<LocalDate> days = new ArrayList<>();
        for (int ago = 6; ago >= 0; ago--) {
            days.add(today.minusDays(ago));
        }
        return days;
    }

    public static String dateProblem(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            LocalDate selected = LocalDate.parse(raw);
            if (selected.isAfter(today) || selected.isBefore(today.minusDays(6))) {
                return "Choose a day from this week";
            }
            return null;
        } catch (DateTimeParseException exception) {
            return "Date must be yyyy-mm-dd";
        }
    }

    public static LocalDate selectedDate(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return today;
        }
        return LocalDate.parse(raw);
    }

    public static String timeValue(LocalTime time) {
        return time.format(TIME);
    }

    public static String timeLabel(LocalTime time) {
        int hour = time.getHour();
        String suffix = hour >= 12 ? "pm" : "am";
        int twelve = hour % 12 == 0 ? 12 : hour % 12;
        if (time.getMinute() == 0) {
            return twelve + ":00 " + suffix;
        }
        return twelve + ":" + String.format("%02d", time.getMinute()) + " " + suffix;
    }

    public static String quantity(BigDecimal amount, String unit) {
        String number = amount.compareTo(HALF) == 0 ? "½" : strip(amount);
        if ("ml".equals(unit)) {
            return number + " ml";
        }
        String noun = amount.compareTo(BigDecimal.ONE) > 0 ? unit + "s" : unit;
        return number + " " + noun;
    }

    public static String doseDetail(BigDecimal amount, String unit, String food) {
        return quantity(amount, unit) + " · " + foodLabel(food);
    }

    public static String stockLine(BigDecimal stock, int supplyDays) {
        String days = supplyDays == 1 ? "1 day" : supplyDays + " days";
        String line = strip(stock) + " left · about " + days;
        if (supplyDays <= LOW_STOCK_DAYS) {
            return line + " · refill soon";
        }
        return line;
    }

    public static String lowTitle(String name, BigDecimal stock) {
        return name + " · " + strip(stock) + " left";
    }

    public static String lowDetail(int supplyDays) {
        String days = supplyDays == 1 ? "1 day" : supplyDays + " days";
        return "About " + days + " of supply";
    }

    public static String strip(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    public static String joinList(List<String> values) {
        return String.join(",", values);
    }

    public static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.copyOf(List.of(raw.split(",")));
    }

    public static Board assemble(
            LocalDate today, LocalTime now, LocalDate selected, List<MedicineSnap> medicines, List<DoseSnap> doses) {
        List<BoardDay> week = new ArrayList<>();
        for (LocalDate day : window(today)) {
            List<DoseSnap> dayDoses = dosesOn(doses, day);
            int total = dayDoses.size();
            int taken = taken(dayDoses);
            week.add(new BoardDay(
                    day,
                    WEEKDAY.format(day),
                    day.getDayOfMonth(),
                    total > 0 && taken == total,
                    day.equals(today),
                    day.equals(selected),
                    percent(taken, total)));
        }
        List<DoseSnap> todayDoses = dosesOn(doses, today);
        List<DoseSnap> selectedDoses = dosesOn(doses, selected);
        List<BoardGroup> groups = new ArrayList<>();
        for (String slot : SLOTS) {
            List<BoardDose> rows = new ArrayList<>();
            for (DoseSnap dose : selectedDoses) {
                if (!slot.equals(slot(dose.time()))) {
                    continue;
                }
                MedicineSnap medicine = find(medicines, dose.medicineId());
                if (medicine == null) {
                    continue;
                }
                rows.add(new BoardDose(
                        dose.id(),
                        dose.medicineId(),
                        medicine.name(),
                        doseDetail(medicine.doseAmount(), medicine.doseUnit(), medicine.food()),
                        timeValue(dose.time()),
                        timeLabel(dose.time()),
                        dose.status(),
                        due(dose, selectedDoses, selected, today, now)));
            }
            rows.sort(Comparator.comparing(BoardDose::time));
            if (!rows.isEmpty()) {
                int groupTaken = 0;
                for (BoardDose row : rows) {
                    if ("taken".equals(row.status())) {
                        groupTaken++;
                    }
                }
                groups.add(new BoardGroup(slot, groupTaken, rows.size(), rows));
            }
        }
        List<BoardMedicine> listed = new ArrayList<>();
        for (MedicineSnap medicine : medicines) {
            int days = supplyDays(
                    medicine.stockUnits(),
                    medicine.unitsPerDose(),
                    medicine.weekdays(),
                    medicine.times().size());
            listed.add(new BoardMedicine(
                    medicine.id(),
                    medicine.name(),
                    summary(medicine),
                    stockLine(medicine.stockUnits(), days),
                    medicine.productId()));
        }
        listed.sort(Comparator.comparing(BoardMedicine::name));
        return new Board(
                new BoardProgress(
                        taken(todayDoses),
                        todayDoses.size(),
                        percent(taken(todayDoses), todayDoses.size()),
                        next(todayDoses, medicines, now)),
                week,
                lowest(medicines),
                groups,
                listed);
    }

    public static int displayAge(Integer ageYears, LocalDate birthday, LocalDate today) {
        if (ageYears != null) {
            return ageYears;
        }
        if (birthday == null || birthday.isAfter(today)) {
            return -1;
        }
        return today.getYear() - birthday.getYear() - (today.getDayOfYear() < birthday.getDayOfYear() ? 1 : 0);
    }

    private static boolean due(
            DoseSnap dose, List<DoseSnap> dayDoses, LocalDate selected, LocalDate today, LocalTime now) {
        if (!selected.equals(today)
                || !"pending".equals(dose.status())
                || dose.time().isAfter(now)) {
            return false;
        }
        for (DoseSnap other : dayDoses) {
            if (!"pending".equals(other.status()) || other.time().isAfter(now)) {
                continue;
            }
            if (other.time().isBefore(dose.time())) {
                return false;
            }
        }
        return true;
    }

    private static String next(List<DoseSnap> todayDoses, List<MedicineSnap> medicines, LocalTime now) {
        DoseSnap upcoming = null;
        DoseSnap overdue = null;
        for (DoseSnap dose : todayDoses) {
            if (!"pending".equals(dose.status())) {
                continue;
            }
            if (!dose.time().isBefore(now) && (upcoming == null || dose.time().isBefore(upcoming.time()))) {
                upcoming = dose;
            }
            if (dose.time().isBefore(now) && (overdue == null || dose.time().isBefore(overdue.time()))) {
                overdue = dose;
            }
        }
        DoseSnap chosen = upcoming != null ? upcoming : overdue;
        if (chosen == null) {
            return null;
        }
        MedicineSnap medicine = find(medicines, chosen.medicineId());
        if (medicine == null) {
            return null;
        }
        return medicine.name() + " · " + timeLabel(chosen.time());
    }

    private static LowStock lowest(List<MedicineSnap> medicines) {
        LowStock best = null;
        for (MedicineSnap medicine : medicines) {
            int days = supplyDays(
                    medicine.stockUnits(),
                    medicine.unitsPerDose(),
                    medicine.weekdays(),
                    medicine.times().size());
            if (days > LOW_STOCK_DAYS) {
                continue;
            }
            if (best == null
                    || days < best.supplyDays()
                    || (days == best.supplyDays() && medicine.stockUnits().compareTo(best.stockUnits()) < 0)
                    || (days == best.supplyDays()
                            && medicine.stockUnits().compareTo(best.stockUnits()) == 0
                            && medicine.name().compareTo(best.name()) < 0)) {
                best = new LowStock(medicine.id(), medicine.productId(), medicine.name(), medicine.stockUnits(), days);
            }
        }
        return best;
    }

    private static String summary(MedicineSnap medicine) {
        List<String> labels = new ArrayList<>();
        for (LocalTime time : medicine.times()) {
            labels.add(timeLabel(time));
        }
        return quantity(medicine.doseAmount(), medicine.doseUnit()) + " · " + String.join(", ", labels) + " · "
                + daysLabel(medicine.weekdays());
    }

    private static String daysLabel(String weekdays) {
        if ("1111111".equals(weekdays)) {
            return "Every day";
        }
        List<String> names = new ArrayList<>();
        for (int index = 0; index < DAY_NAMES.length; index++) {
            if (weekdays.charAt(index) == '1') {
                names.add(DAY_NAMES[index]);
            }
        }
        return String.join(", ", names);
    }

    private static String foodLabel(String food) {
        if ("before".equals(food)) {
            return "before food";
        }
        if ("after".equals(food)) {
            return "after food";
        }
        return "any time";
    }

    private static int percent(int taken, int total) {
        if (total == 0) {
            return 0;
        }
        return taken * 100 / total;
    }

    private static int taken(List<DoseSnap> doses) {
        int count = 0;
        for (DoseSnap dose : doses) {
            if ("taken".equals(dose.status())) {
                count++;
            }
        }
        return count;
    }

    private static List<DoseSnap> dosesOn(List<DoseSnap> doses, LocalDate day) {
        List<DoseSnap> rows = new ArrayList<>();
        for (DoseSnap dose : doses) {
            if (dose.date().equals(day)) {
                rows.add(dose);
            }
        }
        rows.sort(Comparator.comparing(DoseSnap::time));
        return rows;
    }

    private static MedicineSnap find(List<MedicineSnap> medicines, UUID id) {
        for (MedicineSnap medicine : medicines) {
            if (medicine.id().equals(id)) {
                return medicine;
            }
        }
        return null;
    }

    private static boolean creating(Object current) {
        return current == null;
    }

    private static String relation(JsonNode body, MemberValues current, boolean creating) {
        if (!body.has("relation") || body.get("relation").isNull()) {
            if (creating) {
                return null;
            }
            return current.relation();
        }
        if (!body.get("relation").isTextual()) {
            return null;
        }
        String relation = body.get("relation").asText();
        if (creating) {
            return FAMILY_RELATIONS.contains(relation) ? relation : null;
        }
        if ("self".equals(current.relation())) {
            return "self".equals(relation) ? "self" : null;
        }
        return RELATIONS.contains(relation) && !"self".equals(relation) ? relation : null;
    }

    private static String text(JsonNode body, String field, String current, boolean creating) {
        if (!body.has(field) || body.get(field).isNull()) {
            return creating ? "" : current;
        }
        if (!body.get(field).isTextual()) {
            return "";
        }
        return body.get(field).asText().trim();
    }

    private static String optionalText(JsonNode body, String field, String current) {
        if (!body.has(field)) {
            return current;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return "";
        }
        if (!node.isTextual()) {
            return null;
        }
        return node.asText().trim();
    }

    private static Integer integer(JsonNode body, String field, Integer current) {
        if (!body.has(field)) {
            return current;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            return Integer.MIN_VALUE;
        }
        return node.asInt();
    }

    private static LocalDate date(JsonNode body, String field, LocalDate current) {
        if (!body.has(field)) {
            return current;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException exception) {
            return LocalDate.MIN;
        }
    }

    private static UUID uuid(JsonNode body, String field, UUID current) {
        if (!body.has(field) || body.get(field).isNull()) {
            return current;
        }
        if (!body.get(field).isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(body.get(field).asText());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode body, String field, BigDecimal current) {
        if (!body.has(field) || body.get(field).isNull()) {
            return current;
        }
        JsonNode node = body.get(field);
        BigDecimal value;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isTextual()) {
            try {
                value = new BigDecimal(node.asText().trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        } else {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > 2) {
            return null;
        }
        return normalized;
    }

    private static boolean amountOk(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.compareTo(AMOUNT_MAX) <= 0;
    }

    private static String choice(JsonNode body, String field, Set<String> allowed, String current, boolean creating) {
        if (!body.has(field) || body.get(field).isNull()) {
            return creating ? null : current;
        }
        if (!body.get(field).isTextual() || !allowed.contains(body.get(field).asText())) {
            return null;
        }
        return body.get(field).asText();
    }

    private static String weekdays(JsonNode body, String current, boolean creating) {
        if (!body.has("weekdays") || body.get("weekdays").isNull()) {
            return creating ? null : current;
        }
        if (!body.get("weekdays").isTextual()) {
            return null;
        }
        String value = body.get("weekdays").asText();
        if (!value.matches("^[01]{7}$") || "0000000".equals(value)) {
            return null;
        }
        return value;
    }

    private static List<String> tags(JsonNode body, String field, Set<String> allowed, List<String> current) {
        if (!body.has(field)) {
            return current;
        }
        JsonNode node = body.get(field);
        if (!node.isArray()) {
            return null;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !allowed.contains(item.asText())) {
                return null;
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private static List<LocalTime> times(JsonNode body, List<LocalTime> current, boolean creating) {
        if (!body.has("times")) {
            return creating ? null : current;
        }
        JsonNode node = body.get("times");
        if (!node.isArray()) {
            return null;
        }
        LinkedHashSet<LocalTime> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                return null;
            }
            try {
                LocalTime time = LocalTime.parse(item.asText(), TIME);
                if (!timeValue(time).equals(item.asText())) {
                    return null;
                }
                values.add(time);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
        return List.copyOf(values);
    }

    private static boolean flag(JsonNode body, String field, boolean current) {
        if (!body.has(field) || body.get(field).isNull()) {
            return current;
        }
        return body.get(field).asBoolean(current);
    }

    private static <T> Parsed<T> ok(T value) {
        return new Parsed<>(value, null);
    }

    private static <T> Parsed<T> fail(String problem) {
        return new Parsed<>(null, problem);
    }
}
