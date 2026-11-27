package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AppSectionRulesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aSectionNeedsACategoryAndASquareImageFile() throws Exception {
        AppSectionRules.Prepared missing = AppSectionRules.prepare(json.readTree("{}"));
        assertThat(missing.problem()).isEqualTo("Category is required");

        AppSectionRules.Prepared ready =
                AppSectionRules.prepare(json.readTree("{\"category\":\" Pain \",\"position\":2}"));
        assertThat(ready.problem()).isNull();
        assertThat(ready.draft().category()).isEqualTo("Pain");
        assertThat(ready.draft().position()).isEqualTo(2);

        AppSectionRules.Prepared omitted = AppSectionRules.prepare(json.readTree("{\"category\":\"Pain\"}"));
        assertThat(omitted.draft().position()).isZero();

        assertThat(AppSectionRules.prepare(json.readTree("{\"category\":\"Pain\",\"position\":-1}"))
                        .problem())
                .isEqualTo("Position is not valid");
        assertThat(AppSectionRules.imageProblem(null, true)).isEqualTo("Image is required");
        assertThat(AppSectionRules.imageProblem(null, false)).isNull();
        assertThat(AppSectionRules.imageProblem(new AppSectionRules.Upload("text/plain", new byte[] {1}), true))
                .isEqualTo("Image must be an image");
        assertThat(AppSectionRules.imageProblem(new AppSectionRules.Upload("image/jpg", new byte[] {1}), true))
                .isNull();
        assertThat(AppSectionRules.media("image/jpg")).isEqualTo("image/jpeg");
        byte[] huge = new byte[AppSectionRules.IMAGE_MAX + 1];
        assertThat(AppSectionRules.imageProblem(new AppSectionRules.Upload("image/png", huge), true))
                .isEqualTo("Image is too large");
    }
}
