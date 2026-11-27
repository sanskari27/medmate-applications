package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import in.medmate.pharmacy_server.identity.pharmacy.PharmacyRules;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyRulesTest {

    @Test
    void readsABoundedQuery() {
        assertThat(NearbyRules.problem("12.9716", "77.5946", null)).isNull();
        NearbyRules.Query query = NearbyRules.query("12.9716", "77.5946", null);
        assertThat(query.latitude()).isEqualTo(12.9716);
        assertThat(query.longitude()).isEqualTo(77.5946);
        assertThat(query.limit()).isEqualTo(20);

        assertThat(NearbyRules.problem("12.9716", "77.5946", "80")).isNull();
        assertThat(NearbyRules.query("12.9716", "77.5946", "80").limit()).isEqualTo(50);
    }

    @Test
    void rejectsAMissingOrImpossiblePoint() {
        assertThat(NearbyRules.problem(null, "77.5946", null)).isEqualTo("Latitude is required");
        assertThat(NearbyRules.problem("91", "77.5946", null)).isEqualTo("Latitude is out of range");
        assertThat(NearbyRules.problem("12.9716", "nope", null)).isEqualTo("Longitude is out of range");
        assertThat(NearbyRules.problem("12.9716", "77.5946", "0")).isEqualTo("Limit is out of range");
    }

    @Test
    void closesOnInsideTodaysWindowAndClosedOutsideIt() {
        List<PharmacyRules.OpeningHour> hours = List.of(
                new PharmacyRules.OpeningHour("monday", false, "09:00", "13:00"),
                new PharmacyRules.OpeningHour("monday", false, "16:00", "21:00"),
                new PharmacyRules.OpeningHour("sunday", true, null, null),
                new PharmacyRules.OpeningHour("tuesday", false, "21:00", "09:00"));
        NearbyRules.HoursNow closed = new NearbyRules.HoursNow(true, null);

        assertThat(NearbyRules.today(hours, ist("2026-11-23T08:59"))).isEqualTo(closed);
        assertThat(NearbyRules.today(hours, ist("2026-11-23T09:00")))
                .isEqualTo(new NearbyRules.HoursNow(false, "13:00"));
        assertThat(NearbyRules.today(hours, ist("2026-11-23T12:59")))
                .isEqualTo(new NearbyRules.HoursNow(false, "13:00"));
        assertThat(NearbyRules.today(hours, ist("2026-11-23T13:00"))).isEqualTo(closed);
        assertThat(NearbyRules.today(hours, ist("2026-11-23T15:30"))).isEqualTo(closed);
        assertThat(NearbyRules.today(hours, ist("2026-11-23T16:00")))
                .isEqualTo(new NearbyRules.HoursNow(false, "21:00"));
        assertThat(NearbyRules.today(hours, ist("2026-11-23T20:59")))
                .isEqualTo(new NearbyRules.HoursNow(false, "21:00"));
        assertThat(NearbyRules.today(hours, ist("2026-11-23T21:00"))).isEqualTo(closed);
        assertThat(NearbyRules.today(hours, ist("2026-11-29T12:00"))).isEqualTo(closed);
        assertThat(NearbyRules.today(hours, ist("2026-11-24T12:00"))).isEqualTo(closed);
        assertThat(NearbyRules.today(List.of(), ist("2026-11-23T10:00"))).isEqualTo(closed);
        assertThat(NearbyRules.today(null, ist("2026-11-23T10:00"))).isEqualTo(closed);
        assertThat(NearbyRules.openAllHours(null)).isFalse();
        assertThat(NearbyRules.openAllHours(List.of())).isFalse();
        assertThat(NearbyRules.openAllHours(java.util.Collections.nCopies(7, (PharmacyRules.OpeningHour) null)))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour(null, false, "00:00", "23:59"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour("monday", true, "00:00", "23:59"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour("holiday", false, "00:00", "23:59"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(java.util.Collections.nCopies(
                        7, new PharmacyRules.OpeningHour("monday", false, "00:00", "23:59"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour("monday", false, "09:00", "23:59"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour("monday", false, "00:00", "21:00"))))
                .isFalse();
        assertThat(NearbyRules.openAllHours(week(new PharmacyRules.OpeningHour("monday", false, "00:00", "23:59"))))
                .isTrue();
    }

    private static List<PharmacyRules.OpeningHour> week(PharmacyRules.OpeningHour monday) {
        return List.of(
                monday,
                new PharmacyRules.OpeningHour("tuesday", false, "00:00", "23:59"),
                new PharmacyRules.OpeningHour("wednesday", false, "00:00", "23:59"),
                new PharmacyRules.OpeningHour("thursday", false, "00:00", "23:59"),
                new PharmacyRules.OpeningHour("friday", false, "00:00", "23:59"),
                new PharmacyRules.OpeningHour("saturday", false, "00:00", "23:59"),
                new PharmacyRules.OpeningHour("sunday", false, "00:00", "23:59"));
    }

    private static Instant ist(String local) {
        return LocalDateTime.parse(local).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    }
}
