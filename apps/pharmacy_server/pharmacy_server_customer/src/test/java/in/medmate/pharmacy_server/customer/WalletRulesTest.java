package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WalletRulesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void anOrderCreditsFifteenAndAReferralCreditsOneHundred() {
        assertThat(WalletRules.ORDER_CREDITS).isEqualTo(15);
        assertThat(WalletRules.REFERRAL_CREDITS).isEqualTo(100);
    }

    @Test
    void quoteSpendsWholeRupeesAndLeavesPaiseDue() {
        WalletRules.Settlement full = WalletRules.quote(300, new BigDecimal("249.50"));
        assertThat(full.problem()).isNull();
        assertThat(full.balance()).isEqualTo(300);
        assertThat(full.applied()).isEqualTo(249);
        assertThat(full.due()).isEqualByComparingTo("0.50");

        WalletRules.Settlement partial = WalletRules.quote(100, new BigDecimal("249.50"));
        assertThat(partial.applied()).isEqualTo(100);
        assertThat(partial.due()).isEqualByComparingTo("149.50");

        WalletRules.Settlement paise = WalletRules.quote(10, new BigDecimal("0.40"));
        assertThat(paise.applied()).isEqualTo(0);
        assertThat(paise.due()).isEqualByComparingTo("0.40");

        assertThat(WalletRules.quote(15, new BigDecimal("15.00")).due()).isEqualByComparingTo("0.00");
        assertThat(WalletRules.quote(15, new BigDecimal("1000")).applied()).isEqualTo(15);
    }

    @Test
    void quoteRejectsAMissingNegativeOrFractionalPayable() {
        assertThat(WalletRules.payableProblem(null)).isEqualTo("Payable is invalid");
        assertThat(WalletRules.payableProblem(new BigDecimal("-1"))).isEqualTo("Payable must be greater than zero");
        assertThat(WalletRules.payableProblem(new BigDecimal("0"))).isEqualTo("Payable must be greater than zero");
        assertThat(WalletRules.payableProblem(new BigDecimal("1.001"))).isEqualTo("Payable is invalid");
        ObjectNode body = json.createObjectNode();
        assertThat(WalletRules.payableProblem(WalletRules.payable(body))).isEqualTo("Payable is invalid");
        body.put("payable", "nope");
        assertThat(WalletRules.payableProblem(WalletRules.payable(body))).isEqualTo("Payable is invalid");
        body.put("payable", "249.50");
        assertThat(WalletRules.payable(body)).isEqualByComparingTo("249.50");
    }

    @Test
    void aReferralCodeIsEightUnambiguousCharacters() {
        String code = WalletRules.code(new Random(4));
        assertThat(code).hasSize(8).matches("[ABCDEFGHJKMNPQRSTVWXYZ23456789]{8}");
        ObjectNode body = json.createObjectNode();
        body.put("code", " abcd2345 ");
        assertThat(WalletRules.prepareCode(body).problem()).isNull();
        assertThat(WalletRules.prepareCode(body).code()).isEqualTo("ABCD2345");
        body.put("code", "ABCD2340");
        assertThat(WalletRules.prepareCode(body).problem()).isEqualTo("Enter a referral code");
        assertThat(WalletRules.prepareCode(null).problem()).isEqualTo("Enter a referral code");
    }

    @Test
    void aCodeCanBeSavedOnceBeforeTheFirstOrderAndNotForYourself() {
        assertThat(WalletRules.linkProblem(true, false, false, false, false)).isEqualTo("Referral code was not found");
        assertThat(WalletRules.linkProblem(false, true, false, false, false))
                .isEqualTo("You cannot use your own referral code");
        assertThat(WalletRules.linkProblem(false, false, true, false, false))
                .isEqualTo("A referral code is already saved");
        assertThat(WalletRules.linkProblem(false, false, false, true, false))
                .isEqualTo("Referral codes can only be used before your first order");
        assertThat(WalletRules.linkProblem(false, false, false, false, true))
                .isEqualTo("You cannot use each other's referral codes");
        assertThat(WalletRules.linkProblem(false, false, false, false, false)).isNull();
    }
}
