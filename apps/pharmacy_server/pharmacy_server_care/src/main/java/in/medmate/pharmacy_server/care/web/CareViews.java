package in.medmate.pharmacy_server.care.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CareViews {

    private CareViews() {}

    public record Person(UUID id, String name, String relation) {}

    public record Progress(int taken, int total, int percent, String next) {}

    public record WeekDay(
            String date, String weekday, int day, boolean complete, boolean today, boolean selected, int percent) {}

    public record Dose(
            UUID id,
            UUID medicineId,
            String name,
            String detail,
            String time,
            String timeLabel,
            String status,
            boolean due) {}

    public record Group(String slot, int taken, int total, List<Dose> doses) {}

    public record LowStock(UUID medicineId, String productId, String title, String detail) {}

    public record MedicineLine(UUID id, String name, String summary, String stockLine, String productId) {}

    public record Board(
            String date,
            String today,
            UUID memberId,
            List<Person> people,
            Progress progress,
            List<WeekDay> week,
            LowStock lowStock,
            List<Group> groups,
            List<MedicineLine> medicines,
            UUID prescriptionId) {}

    public record Member(
            UUID id,
            String displayName,
            String relation,
            Integer ageYears,
            String birthday,
            List<String> conditions,
            List<String> allergies,
            String mobile,
            boolean consent,
            int medicineCount,
            int dosesTakenToday,
            int dosesToday) {}

    public record MemberList(String today, List<Member> members) {}

    public record MemberOne(Member member) {}

    public record Medicine(
            UUID id,
            UUID memberId,
            String productId,
            String name,
            BigDecimal doseAmount,
            String doseUnit,
            String food,
            String weekdays,
            String startsOn,
            String endsOn,
            BigDecimal stockUnits,
            BigDecimal unitsPerDose,
            boolean reminders,
            String note,
            List<String> times,
            int supplyDays) {}

    public record MedicineOne(Medicine medicine) {}

    public record DoseOne(UUID id, String status) {}

    public record DoseResponse(DoseOne dose) {}

    public record PrescriptionSummary(UUID id, String source, String issuedOn, boolean open) {}

    public record PrescriptionList(List<PrescriptionSummary> prescriptions) {}

    public record PrescriptionLine(
            UUID id, String productId, String name, String doseLabel, boolean scheduled, boolean rx) {}

    public record Prescription(UUID id, String source, String issuedOn, List<PrescriptionLine> lines) {}

    public record PrescriptionOne(Prescription prescription) {}

    public record Scheduled(int scheduled) {}
}
