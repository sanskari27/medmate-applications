package in.medmate.pharmacy_server.care.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.care.CareRules;
import in.medmate.pharmacy_server.care.store.CareStore;
import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CareService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final CareStore store;
    private final CatalogStore catalog;
    private final Clock clock;

    public CareService(CareStore store, CatalogStore catalog, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public CareViews.MemberList family(AccountPrincipal actor) {
        requireCustomer(actor);
        ensureSelf(actor);
        LocalDate today = today();
        List<CareRules.MedicineSnap> medicines = snaps(actor.id(), null);
        ensureDoses(actor.id(), today, medicines);
        return members(actor.id(), today, medicines, store.doses(actor.id(), today, today, null));
    }

    @Transactional
    public CareViews.MemberOne createMember(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        CareRules.Parsed<CareRules.MemberValues> parsed = CareRules.parseMember(body, null);
        if (parsed.problem() != null) {
            throw bad(parsed.problem());
        }
        UUID id = UUID.randomUUID();
        store.insertMember(id, actor.id(), parsed.value(), clock.instant());
        return new CareViews.MemberOne(memberView(store.member(actor.id(), id).orElseThrow(), 0, 0, 0));
    }

    @Transactional(readOnly = true)
    public CareViews.MemberOne member(AccountPrincipal actor, UUID id) {
        requireCustomer(actor);
        return new CareViews.MemberOne(memberView(requireMember(actor.id(), id), 0, 0, 0));
    }

    @Transactional
    public CareViews.MemberOne updateMember(AccountPrincipal actor, UUID id, JsonNode body) {
        requireCustomer(actor);
        CareStore.MemberRow current = requireMember(actor.id(), id);
        CareRules.Parsed<CareRules.MemberValues> parsed = CareRules.parseMember(body, values(current));
        if (parsed.problem() != null) {
            throw bad(parsed.problem());
        }
        store.updateMember(id, actor.id(), parsed.value(), clock.instant());
        return new CareViews.MemberOne(memberView(store.member(actor.id(), id).orElseThrow(), 0, 0, 0));
    }

    @Transactional
    public CareViews.Board dosage(AccountPrincipal actor, String memberId, String date) {
        requireCustomer(actor);
        ensureSelf(actor);
        LocalDate today = today();
        String dateProblem = CareRules.dateProblem(date, today);
        if (dateProblem != null) {
            throw bad(dateProblem);
        }
        UUID member = memberFilter(actor.id(), memberId);
        LocalDate selected = CareRules.selectedDate(date, today);
        List<CareRules.MedicineSnap> medicines = snaps(actor.id(), member);
        ensureDoses(actor.id(), today, medicines);
        List<CareStore.DoseRow> doses = store.doses(actor.id(), today.minusDays(6), today, member);
        CareRules.Board board = CareRules.assemble(today, now(), selected, medicines, doseSnaps(doses));
        return boardView(
                today,
                selected,
                member,
                store.members(actor.id()),
                board,
                store.latestOpen(actor.id()).orElse(null));
    }

    @Transactional
    public CareViews.DoseResponse markDose(AccountPrincipal actor, UUID id, String action) {
        requireCustomer(actor);
        CareStore.DoseActionRow dose = store.findDose(actor.id(), id).orElseThrow(() -> missing("Dose was not found"));
        CareRules.Transition transition = CareRules.transition(dose.status(), action, dose.unitsPerDose());
        if (transition == null) {
            throw bad("This dose cannot be changed");
        }
        Instant acted = "pending".equals(transition.status()) ? null : clock.instant();
        store.markDose(actor.id(), id, transition.status(), acted);
        if (transition.stockDelta().signum() != 0) {
            store.addStock(actor.id(), dose.medicineId(), transition.stockDelta(), clock.instant());
        }
        return new CareViews.DoseResponse(new CareViews.DoseOne(id, transition.status()));
    }

    @Transactional
    public CareViews.MedicineOne createMedicine(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        ensureSelf(actor);
        CareRules.Parsed<CareRules.MedicineValues> parsed = CareRules.parseMedicine(body, null);
        if (parsed.problem() != null) {
            throw bad(parsed.problem());
        }
        requireMember(actor.id(), parsed.value().memberId());
        requireProduct(parsed.value().productId());
        UUID id = UUID.randomUUID();
        store.insertMedicine(id, actor.id(), parsed.value(), null, clock.instant());
        store.replaceTimes(id, parsed.value().times());
        return new CareViews.MedicineOne(medicineView(
                store.medicine(actor.id(), id).orElseThrow(), parsed.value().times()));
    }

    @Transactional(readOnly = true)
    public CareViews.MedicineOne medicine(AccountPrincipal actor, UUID id) {
        requireCustomer(actor);
        CareStore.MedicineRow row = requireMedicine(actor.id(), id);
        return new CareViews.MedicineOne(medicineView(row, timesOf(actor.id(), id)));
    }

    @Transactional
    public CareViews.MedicineOne updateMedicine(AccountPrincipal actor, UUID id, JsonNode body) {
        requireCustomer(actor);
        CareStore.MedicineRow current = requireMedicine(actor.id(), id);
        List<LocalTime> currentTimes = timesOf(actor.id(), id);
        CareRules.Parsed<CareRules.MedicineValues> parsed =
                CareRules.parseMedicine(body, medicineValues(current, currentTimes));
        if (parsed.problem() != null) {
            throw bad(parsed.problem());
        }
        requireMember(actor.id(), parsed.value().memberId());
        requireProduct(parsed.value().productId());
        store.updateMedicine(id, actor.id(), parsed.value(), clock.instant());
        store.replaceTimes(id, parsed.value().times());
        store.deletePendingFrom(id, today());
        return new CareViews.MedicineOne(medicineView(
                store.medicine(actor.id(), id).orElseThrow(), parsed.value().times()));
    }

    @Transactional(readOnly = true)
    public CareViews.PrescriptionList prescriptions(AccountPrincipal actor) {
        requireCustomer(actor);
        List<CareViews.PrescriptionSummary> rows = new ArrayList<>();
        for (CareStore.PrescriptionRow prescription : store.prescriptions(actor.id())) {
            rows.add(summary(prescription));
        }
        return new CareViews.PrescriptionList(rows);
    }

    @Transactional(readOnly = true)
    public CareViews.PrescriptionOne prescription(AccountPrincipal actor, UUID id) {
        requireCustomer(actor);
        CareStore.PrescriptionRow prescription =
                store.prescription(actor.id(), id).orElseThrow(() -> missing("Prescription was not found"));
        return new CareViews.PrescriptionOne(detail(prescription, store.lines(id)));
    }

    @Transactional
    public CareViews.Scheduled schedule(AccountPrincipal actor, UUID id, JsonNode body) {
        requireCustomer(actor);
        ensureSelf(actor);
        if (store.prescription(actor.id(), id).isEmpty()) {
            throw missing("Prescription was not found");
        }
        CareRules.Parsed<CareRules.ScheduleRequest> parsed = CareRules.parseSchedule(body);
        if (parsed.problem() != null) {
            throw bad(parsed.problem());
        }
        requireMember(actor.id(), parsed.value().memberId());
        LocalDate today = today();
        int scheduled = 0;
        for (CareRules.ScheduleLine line : parsed.value().lines()) {
            CareStore.LineRow stored =
                    store.line(id, line.lineId()).orElseThrow(() -> missing("Prescription was not found"));
            if (stored.scheduled()) {
                throw bad("This medicine is already on a schedule");
            }
            requireProduct(stored.productId());
            LocalDate ends = today.plusDays(line.days() - 1L);
            CareRules.MedicineValues values = new CareRules.MedicineValues(
                    parsed.value().memberId(),
                    stored.productId(),
                    stored.name(),
                    line.doseAmount(),
                    line.doseUnit(),
                    line.food(),
                    line.weekdays(),
                    today,
                    ends,
                    line.stockUnits(),
                    true,
                    null,
                    line.times());
            UUID medicineId = UUID.randomUUID();
            store.insertMedicine(medicineId, actor.id(), values, stored.id(), clock.instant());
            store.replaceTimes(medicineId, line.times());
            store.markLineScheduled(stored.id());
            scheduled++;
        }
        return new CareViews.Scheduled(scheduled);
    }

    private CareViews.MemberList members(
            UUID accountId, LocalDate today, List<CareRules.MedicineSnap> medicines, List<CareStore.DoseRow> doses) {
        List<CareViews.Member> rows = new ArrayList<>();
        for (CareStore.MemberRow member : store.members(accountId)) {
            int count = 0;
            for (CareRules.MedicineSnap medicine : medicines) {
                if (medicine.memberId().equals(member.id())) {
                    count++;
                }
            }
            int total = 0;
            int taken = 0;
            for (CareStore.DoseRow dose : doses) {
                if (!dose.memberId().equals(member.id()) || !dose.date().equals(today)) {
                    continue;
                }
                total++;
                if ("taken".equals(dose.status())) {
                    taken++;
                }
            }
            rows.add(memberView(member, count, taken, total));
        }
        return new CareViews.MemberList(today.toString(), rows);
    }

    private CareViews.Member memberView(CareStore.MemberRow row, int medicines, int taken, int total) {
        int age = CareRules.displayAge(row.ageYears(), row.birthday(), today());
        return new CareViews.Member(
                row.id(),
                row.displayName(),
                row.relation(),
                age < 0 ? null : age,
                row.birthday() == null ? null : row.birthday().toString(),
                CareRules.splitList(row.conditions()),
                CareRules.splitList(row.allergies()),
                row.mobile(),
                row.consent(),
                medicines,
                taken,
                total);
    }

    private CareViews.Board boardView(
            LocalDate today,
            LocalDate selected,
            UUID memberId,
            List<CareStore.MemberRow> people,
            CareRules.Board board,
            UUID prescriptionId) {
        List<CareViews.Person> persons = new ArrayList<>();
        for (CareStore.MemberRow person : people) {
            persons.add(new CareViews.Person(person.id(), person.displayName(), person.relation()));
        }
        List<CareViews.WeekDay> week = new ArrayList<>();
        for (CareRules.BoardDay day : board.week()) {
            week.add(new CareViews.WeekDay(
                    day.date().toString(),
                    day.weekday(),
                    day.day(),
                    day.complete(),
                    day.today(),
                    day.selected(),
                    day.percent()));
        }
        List<CareViews.Group> groups = new ArrayList<>();
        for (CareRules.BoardGroup group : board.groups()) {
            List<CareViews.Dose> doses = new ArrayList<>();
            for (CareRules.BoardDose dose : group.doses()) {
                doses.add(new CareViews.Dose(
                        dose.id(),
                        dose.medicineId(),
                        dose.name(),
                        dose.detail(),
                        dose.time(),
                        dose.timeLabel(),
                        dose.status(),
                        dose.due()));
            }
            groups.add(new CareViews.Group(group.slot(), group.taken(), group.total(), doses));
        }
        List<CareViews.MedicineLine> medicines = new ArrayList<>();
        for (CareRules.BoardMedicine medicine : board.medicines()) {
            medicines.add(new CareViews.MedicineLine(
                    medicine.id(), medicine.name(), medicine.summary(), medicine.stockLine(), medicine.productId()));
        }
        CareViews.LowStock low = null;
        if (board.lowStock() != null) {
            low = new CareViews.LowStock(
                    board.lowStock().medicineId(),
                    board.lowStock().productId(),
                    CareRules.lowTitle(board.lowStock().name(), board.lowStock().stockUnits()),
                    CareRules.lowDetail(board.lowStock().supplyDays()));
        }
        return new CareViews.Board(
                selected.toString(),
                today.toString(),
                memberId,
                persons,
                new CareViews.Progress(
                        board.progress().taken(),
                        board.progress().total(),
                        board.progress().percent(),
                        board.progress().next()),
                week,
                low,
                groups,
                medicines,
                prescriptionId);
    }

    private CareViews.Medicine medicineView(CareStore.MedicineRow row, List<LocalTime> times) {
        List<String> labels = new ArrayList<>();
        for (LocalTime time : times) {
            labels.add(CareRules.timeValue(time));
        }
        int supply = CareRules.supplyDays(row.stockUnits(), row.unitsPerDose(), row.weekdays(), times.size());
        return new CareViews.Medicine(
                row.id(),
                row.memberId(),
                row.productId(),
                row.name(),
                row.doseAmount(),
                row.doseUnit(),
                row.food(),
                row.weekdays(),
                row.startsOn().toString(),
                row.endsOn() == null ? null : row.endsOn().toString(),
                row.stockUnits(),
                row.unitsPerDose(),
                row.reminders(),
                row.note(),
                labels,
                supply);
    }

    private CareViews.PrescriptionSummary summary(CareStore.PrescriptionRow row) {
        return new CareViews.PrescriptionSummary(
                row.id(), row.source(), row.issuedOn().toString(), row.open());
    }

    private CareViews.Prescription detail(CareStore.PrescriptionRow row, List<CareStore.LineRow> lines) {
        Map<String, Boolean> rx = rxFlags(lines);
        List<CareViews.PrescriptionLine> views = new ArrayList<>();
        for (CareStore.LineRow line : lines) {
            views.add(new CareViews.PrescriptionLine(
                    line.id(),
                    line.productId(),
                    line.name(),
                    line.doseLabel(),
                    line.scheduled(),
                    line.productId() != null && Boolean.TRUE.equals(rx.get(line.productId()))));
        }
        return new CareViews.Prescription(row.id(), row.source(), row.issuedOn().toString(), views);
    }

    private Map<String, Boolean> rxFlags(List<CareStore.LineRow> lines) {
        List<String> ids = new ArrayList<>();
        for (CareStore.LineRow line : lines) {
            if (line.productId() != null && !ids.contains(line.productId())) {
                ids.add(line.productId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        CatalogStore.Found found = catalog.findMany(ids);
        Map<String, Boolean> flags = new HashMap<>();
        for (CatalogDocuments.DrugDetail drug : found.drug()) {
            flags.put(drug.productId(), drug.prescriptionRequired());
        }
        return flags;
    }

    private void ensureSelf(AccountPrincipal actor) {
        if (store.self(actor.id()).isPresent()) {
            return;
        }
        store.insertMember(UUID.randomUUID(), actor.id(), CareRules.selfMember(actor.displayName()), clock.instant());
    }

    private void ensureDoses(UUID accountId, LocalDate today, List<CareRules.MedicineSnap> medicines) {
        for (LocalDate day : CareRules.window(today)) {
            for (CareRules.MedicineSnap medicine : medicines) {
                if (!CareRules.activeOn(day, medicine.startsOn(), medicine.endsOn(), medicine.weekdays())) {
                    continue;
                }
                for (LocalTime time : medicine.times()) {
                    store.ensureDose(UUID.randomUUID(), accountId, medicine.id(), medicine.memberId(), day, time);
                }
            }
        }
    }

    private List<CareRules.MedicineSnap> snaps(UUID accountId, UUID memberId) {
        Map<UUID, List<LocalTime>> times = new HashMap<>();
        for (CareStore.TimeRow time : store.times(accountId)) {
            times.computeIfAbsent(time.medicineId(), ignored -> new ArrayList<>())
                    .add(time.time());
        }
        List<CareRules.MedicineSnap> snaps = new ArrayList<>();
        for (CareStore.MedicineRow row : store.medicines(accountId, memberId)) {
            snaps.add(new CareRules.MedicineSnap(
                    row.id(),
                    row.memberId(),
                    row.productId(),
                    row.name(),
                    row.doseAmount(),
                    row.doseUnit(),
                    row.food(),
                    row.weekdays(),
                    row.startsOn(),
                    row.endsOn(),
                    row.stockUnits(),
                    row.unitsPerDose(),
                    times.getOrDefault(row.id(), List.of())));
        }
        return snaps;
    }

    private List<LocalTime> timesOf(UUID accountId, UUID medicineId) {
        List<LocalTime> times = new ArrayList<>();
        for (CareStore.TimeRow time : store.times(accountId)) {
            if (time.medicineId().equals(medicineId)) {
                times.add(time.time());
            }
        }
        return times;
    }

    private static List<CareRules.DoseSnap> doseSnaps(List<CareStore.DoseRow> doses) {
        List<CareRules.DoseSnap> snaps = new ArrayList<>();
        for (CareStore.DoseRow dose : doses) {
            snaps.add(new CareRules.DoseSnap(dose.id(), dose.medicineId(), dose.date(), dose.time(), dose.status()));
        }
        return snaps;
    }

    private static CareRules.MemberValues values(CareStore.MemberRow row) {
        return new CareRules.MemberValues(
                row.displayName(),
                row.relation(),
                row.ageYears(),
                row.birthday(),
                CareRules.splitList(row.conditions()),
                CareRules.splitList(row.allergies()),
                row.mobile(),
                row.consent());
    }

    private static CareRules.MedicineValues medicineValues(CareStore.MedicineRow row, List<LocalTime> times) {
        return new CareRules.MedicineValues(
                row.memberId(),
                row.productId(),
                row.name(),
                row.doseAmount(),
                row.doseUnit(),
                row.food(),
                row.weekdays(),
                row.startsOn(),
                row.endsOn(),
                row.stockUnits(),
                row.reminders(),
                row.note(),
                times);
    }

    private UUID memberFilter(UUID accountId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw bad("Choose who this schedule is for");
        }
        requireMember(accountId, id);
        return id;
    }

    private CareStore.MemberRow requireMember(UUID accountId, UUID id) {
        return store.member(accountId, id).orElseThrow(() -> missing("Family member was not found"));
    }

    private CareStore.MedicineRow requireMedicine(UUID accountId, UUID id) {
        return store.medicine(accountId, id).orElseThrow(() -> missing("Medicine was not found"));
    }

    private void requireProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }
        CatalogStore.Found found = catalog.findMany(List.of(productId));
        if (!found.missing().isEmpty()) {
            throw bad("Medicine was not found in the catalogue");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private LocalTime now() {
        return LocalTime.now(clock.withZone(ZONE));
    }

    private static void requireCustomer(AccountPrincipal actor) {
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
    }

    private static ApiProblem bad(String detail) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private static ApiProblem missing(String detail) {
        return new ApiProblem(HttpStatus.NOT_FOUND, "Not found", detail);
    }
}
