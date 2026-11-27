package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

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
}
