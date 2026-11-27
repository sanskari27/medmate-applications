package in.medmate.pharmacy_server.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareRulesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);
    private static final UUID TELMA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CALCIUM = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-4000-8000-000000000010");

    @Test
    void namesTheAccountHolderAndCountsSupplyTheWayTheScheduleDoes() {
        assertThat(CareRules.selfName(null)).isEqualTo("You");
        assertThat(CareRules.selfName("  ")).isEqualTo("You");
        assertThat(CareRules.selfName(" Priya ")).isEqualTo("Priya");
        assertThat(CareRules.selfName("P".repeat(90))).hasSize(80);
        assertThat(CareRules.supplyDays(new BigDecimal("4"), BigDecimal.ONE, "1111111", 1))
                .isEqualTo(4);
        assertThat(CareRules.supplyDays(new BigDecimal("28"), BigDecimal.ONE, "1111111", 2))
                .isEqualTo(14);
        assertThat(CareRules.supplyDays(new BigDecimal("12"), BigDecimal.ONE, "1010100", 1))
                .isEqualTo(28);
        assertThat(CareRules.supplyDays(BigDecimal.ZERO, BigDecimal.ZERO, "1111111", 1))
                .isZero();
        assertThat(CareRules.supplyDays(BigDecimal.ONE, BigDecimal.ONE, "0000000", 1))
                .isZero();
    }

    @Test
    void groupsTheDayAndDescribesADose() {
        assertThat(CareRules.slot(LocalTime.of(4, 59))).isEqualTo("night");
        assertThat(CareRules.slot(LocalTime.of(5, 0))).isEqualTo("morning");
        assertThat(CareRules.slot(LocalTime.of(11, 59))).isEqualTo("morning");
        assertThat(CareRules.slot(LocalTime.of(12, 0))).isEqualTo("afternoon");
        assertThat(CareRules.slot(LocalTime.of(16, 59))).isEqualTo("afternoon");
        assertThat(CareRules.slot(LocalTime.of(17, 0))).isEqualTo("evening");
        assertThat(CareRules.slot(LocalTime.of(21, 59))).isEqualTo("evening");
        assertThat(CareRules.slot(LocalTime.of(22, 0))).isEqualTo("night");
        assertThat(CareRules.quantity(new BigDecimal("0.5"), "tablet")).isEqualTo("½ tablet");
        assertThat(CareRules.quantity(new BigDecimal("2"), "tablet")).isEqualTo("2 tablets");
        assertThat(CareRules.quantity(new BigDecimal("5"), "ml")).isEqualTo("5 ml");
        assertThat(CareRules.doseDetail(BigDecimal.ONE, "tablet", "after")).isEqualTo("1 tablet · after food");
        assertThat(CareRules.doseDetail(BigDecimal.ONE, "capsule", "before")).isEqualTo("1 capsule · before food");
        assertThat(CareRules.doseDetail(BigDecimal.ONE, "tablet", "any")).isEqualTo("1 tablet · any time");
        assertThat(CareRules.timeLabel(LocalTime.of(8, 0))).isEqualTo("8:00 am");
        assertThat(CareRules.timeLabel(LocalTime.of(8, 30))).isEqualTo("8:30 am");
        assertThat(CareRules.timeLabel(LocalTime.of(0, 0))).isEqualTo("12:00 am");
        assertThat(CareRules.timeLabel(LocalTime.of(12, 0))).isEqualTo("12:00 pm");
        assertThat(CareRules.stockLine(new BigDecimal("4"), 4)).isEqualTo("4 left · about 4 days · refill soon");
        assertThat(CareRules.stockLine(new BigDecimal("28"), 14)).isEqualTo("28 left · about 14 days");
        assertThat(CareRules.stockLine(BigDecimal.ONE, 1)).isEqualTo("1 left · about 1 day · refill soon");
        assertThat(CareRules.lowDetail(1)).isEqualTo("About 1 day of supply");
    }

    @Test
    void buildsTodayAndTheSixDaysBeforeIt() throws Exception {
        CareRules.MedicineSnap telma = medicine(TELMA, "Telma-R 5 Tablet", "4", "1111111", List.of(LocalTime.of(8, 0)));
        CareRules.MedicineSnap calcium =
                medicine(CALCIUM, "Calcium", "28", "1111111", List.of(LocalTime.of(8, 30), LocalTime.of(21, 0)));
        UUID morning = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        UUID evening = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
        UUID night = UUID.fromString("00000000-0000-4000-8000-0000000000a3");
        List<CareRules.DoseSnap> doses = List.of(
                new CareRules.DoseSnap(morning, TELMA, TODAY, LocalTime.of(8, 0), "taken"),
                new CareRules.DoseSnap(evening, CALCIUM, TODAY, LocalTime.of(8, 30), "taken"),
                new CareRules.DoseSnap(night, CALCIUM, TODAY, LocalTime.of(21, 0), "pending"),
                new CareRules.DoseSnap(UUID.randomUUID(), TELMA, TODAY.minusDays(1), LocalTime.of(8, 0), "taken"));
        CareRules.Board board = CareRules.assemble(TODAY, LocalTime.of(20, 10), TODAY, List.of(telma, calcium), doses);

        assertThat(board.progress().taken()).isEqualTo(2);
        assertThat(board.progress().total()).isEqualTo(3);
        assertThat(board.progress().percent()).isEqualTo(66);
        assertThat(board.progress().next()).isEqualTo("Calcium · 9:00 pm");
        assertThat(board.week()).hasSize(7);
        assertThat(board.week().get(6).today()).isTrue();
        assertThat(board.week().get(6).selected()).isTrue();
        assertThat(board.week().get(5).complete()).isTrue();
        assertThat(board.lowStock().medicineId()).isEqualTo(TELMA);
        assertThat(board.lowStock().supplyDays()).isEqualTo(4);
        assertThat(board.groups()).extracting(CareRules.BoardGroup::slot).containsExactly("morning", "evening");
        assertThat(board.groups().get(1).doses().get(0).due()).isFalse();
        assertThat(board.medicines())
                .extracting(CareRules.BoardMedicine::summary)
                .contains("1 tablet · 8:00 am · Every day", "1 tablet · 8:30 am, 9:00 pm · Every day");
        assertThat(board.medicines().get(0).name()).isEqualTo("Calcium");

        CareRules.Board earlier =
                CareRules.assemble(TODAY, LocalTime.of(20, 10), TODAY.minusDays(1), List.of(telma), doses);
        assertThat(earlier.groups()).hasSize(1);
        assertThat(earlier.groups().get(0).doses().get(0).due()).isFalse();
        assertThat(CareRules.dateProblem("2026-11-23", TODAY)).isEqualTo("Choose a day from this week");
        assertThat(CareRules.dateProblem("30-11-2026", TODAY)).isEqualTo("Date must be yyyy-mm-dd");
        assertThat(CareRules.dateProblem(null, TODAY)).isNull();
        assertThat(CareRules.selectedDate("", TODAY)).isEqualTo(TODAY);
        assertThat(CareRules.activeOn(TODAY, TODAY, null, "1000000")).isTrue();
        assertThat(CareRules.activeOn(TODAY.minusDays(1), TODAY, null, "1111111"))
                .isFalse();
        assertThat(CareRules.activeOn(TODAY.plusDays(1), TODAY, TODAY, "1111111"))
                .isFalse();
        assertThat(JSON.readTree("{}")).isNotNull();
    }

    @Test
    void acceptsAFamilyMemberAndAMedicineAndRejectsTheRest() throws Exception {
        CareRules.Parsed<CareRules.MemberValues> created = CareRules.parseMember(
                JSON.readTree(
                        """
                        {"displayName":" Amma ","relation":"mother","ageYears":62,"conditions":["diabetes","diabetes"],
                         "allergies":["penicillin"],"mobile":"9845012345","consent":true}
                        """),
                null);
        assertThat(created.problem()).isNull();
        assertThat(created.value().displayName()).isEqualTo("Amma");
        assertThat(created.value().mobile()).isEqualTo("+919845012345");
        assertThat(created.value().conditions()).containsExactly("diabetes");

        assertThat(CareRules.parseMember(null, null).problem()).isEqualTo("Family member details are required");
        assertThat(CareRules.parseMember(
                                JSON.readTree("{\"relation\":\"self\",\"displayName\":\"A\",\"consent\":true}"), null)
                        .problem())
                .isEqualTo("Choose a family relation");
        assertThat(CareRules.parseMember(
                                JSON.readTree(
                                        "{\"relation\":\"mother\",\"displayName\":\" \",\"ageYears\":1,\"consent\":true}"),
                                null)
                        .problem())
                .isEqualTo("Name is required");
        assertThat(CareRules.parseMember(
                                JSON.readTree("{\"relation\":\"mother\",\"displayName\":\"" + "A".repeat(81)
                                        + "\",\"ageYears\":1,\"consent\":true}"),
                                null)
                        .problem())
                .isEqualTo("Name must be 80 characters or fewer");
        assertThat(CareRules.parseMember(
                                JSON.readTree("{\"relation\":\"mother\",\"displayName\":\"Amma\",\"consent\":true}"),
                                null)
                        .problem())
                .isEqualTo("Age or birthday is required");
        assertThat(CareRules.parseMember(
                                JSON.readTree(
                                        "{\"relation\":\"mother\",\"displayName\":\"Amma\",\"ageYears\":62,\"birthday\":\"1964-01-01\",\"consent\":true}"),
                                null)
                        .problem())
                .isEqualTo("Give an age or a birthday, not both");
        assertThat(CareRules.parseMember(
                                JSON.readTree(
                                        "{\"relation\":\"mother\",\"displayName\":\"Amma\",\"ageYears\":1,\"consent\":false}"),
                                null)
                        .problem())
                .isEqualTo("Add this person only with their permission");
        assertThat(CareRules.parseMember(
                                JSON.readTree(
                                        "{\"relation\":\"mother\",\"displayName\":\"Amma\",\"ageYears\":1,\"consent\":true,\"mobile\":\"123\"}"),
                                null)
                        .problem())
                .isEqualTo("Mobile number must be an Indian mobile");

        CareRules.MemberValues current = created.value();
        assertThat(CareRules.parseMember(null, current).value()).isEqualTo(current);
        assertThat(CareRules.parseMember(JSON.readTree("{\"relation\":\"self\"}"), current)
                        .problem())
                .isEqualTo("The account holder stays on the schedule");

        CareRules.Parsed<CareRules.MedicineValues> medicine = CareRules.parseMedicine(
                JSON.readTree(
                        """
                        {"memberId":"00000000-0000-4000-8000-000000000010","name":"Telma-R 5 Tablet","doseAmount":1,
                         "doseUnit":"tablet","food":"after","weekdays":"1111111","startsOn":"2026-11-30",
                         "stockUnits":4,"times":["08:00","20:00"],"note":"  "}
                        """),
                null);
        assertThat(medicine.problem()).isNull();
        assertThat(medicine.value().note()).isNull();
        assertThat(medicine.value().times()).containsExactly(LocalTime.of(8, 0), LocalTime.of(20, 0));
        assertThat(CareRules.parseMedicine(JSON.readTree("{}"), null).problem())
                .isEqualTo("Choose who this medicine is for");
        assertThat(CareRules.parseMedicine(null, null).problem()).isEqualTo("Medicine details are required");
        assertThat(CareRules.parseMedicine(null, medicine.value()).value()).isEqualTo(medicine.value());

        CareRules.Parsed<CareRules.ScheduleRequest> schedule = CareRules.parseSchedule(
                JSON.readTree(
                        """
                        {"memberId":"00000000-0000-4000-8000-000000000010","lines":[{"lineId":"00000000-0000-4000-8000-000000000099",
                         "doseAmount":1,"doseUnit":"tablet","food":"after","weekdays":"1111111","days":5,"stockUnits":5,"times":["08:00"]}]}
                        """));
        assertThat(schedule.problem()).isNull();
        assertThat(schedule.value().lines()).hasSize(1);
        assertThat(CareRules.parseSchedule(JSON.readTree("{}")).problem())
                .isEqualTo("Choose who these medicines are for");
        assertThat(CareRules.parseSchedule(null).problem()).isEqualTo("Choose at least one medicine");
        assertThat(CareRules.transition("pending", "taken", BigDecimal.ONE).stockDelta())
                .isEqualByComparingTo("-1");
        assertThat(CareRules.transition("pending", "skipped", BigDecimal.ONE).status())
                .isEqualTo("skipped");
        assertThat(CareRules.transition("taken", "pending", BigDecimal.ONE).stockDelta())
                .isEqualByComparingTo("1");
        assertThat(CareRules.transition("skipped", "pending", BigDecimal.ONE).stockDelta())
                .isEqualByComparingTo("0");
        assertThat(CareRules.transition("taken", "skipped", BigDecimal.ONE)).isNull();
        assertThat(CareRules.displayAge(null, LocalDate.of(1964, 1, 2), TODAY)).isEqualTo(62);
        assertThat(CareRules.displayAge(null, null, TODAY)).isEqualTo(-1);
        assertThat(CareRules.splitList("")).isEmpty();
        assertThat(CareRules.splitList("diabetes,high_bp")).containsExactly("diabetes", "high_bp");
        assertThat(MEMBER).isNotNull();
    }

    private static CareRules.MedicineSnap medicine(
            UUID id, String name, String stock, String weekdays, List<LocalTime> times) {
        return new CareRules.MedicineSnap(
                id,
                MEMBER,
                "CARE1",
                name,
                BigDecimal.ONE,
                "tablet",
                "after",
                weekdays,
                TODAY,
                null,
                new BigDecimal(stock),
                BigDecimal.ONE,
                times);
    }
}
