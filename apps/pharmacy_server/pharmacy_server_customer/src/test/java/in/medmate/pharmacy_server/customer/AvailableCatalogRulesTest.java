package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvailableCatalogRulesTest {

    @Test
    void blankFiltersListEverythingAndAShortQueryIsRejected() {
        AvailableCatalogRules.Browse open = AvailableCatalogRules.browse(null, "  ");
        assertThat(open.problem()).isNull();
        assertThat(open.category()).isEmpty();
        assertThat(open.query()).isEmpty();

        AvailableCatalogRules.Browse filtered = AvailableCatalogRules.browse("  Pain ", " Crocin ");
        assertThat(filtered.category()).isEqualTo("Pain");
        assertThat(filtered.query()).isEqualTo("Crocin");

        assertThat(AvailableCatalogRules.browse("Pain", "a").problem()).isEqualTo("Query is not valid");
        assertThat(AvailableCatalogRules.browse("x".repeat(81), null).problem()).isEqualTo("Category is not valid");
    }
}
