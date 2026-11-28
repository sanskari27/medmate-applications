package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppSectionRulesTest {

    private static final UUID IMAGE = UUID.fromString("00000000-0000-4000-8000-0000000000d3");

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aSectionNeedsANameBannerAndCategoriesWithImages() throws Exception {
        assertThat(AppSectionRules.prepare(json.readTree("{}")).problem()).isEqualTo("Name is required");
        assertThat(AppSectionRules.prepare(json.readTree("{\"title\":\"Care\"}"))
                        .problem())
                .isEqualTo("Add a category");

        AppSectionRules.Prepared ready = AppSectionRules.prepare(json.readTree(
                """
                {"title":" Everyday care ","position":2,"categories":[
                  {"category":" Pain ","images":[{"file":true},{"id":"%s"}]}
                ]}
                """
                        .formatted(IMAGE)));
        assertThat(ready.problem()).isNull();
        assertThat(ready.draft().title()).isEqualTo("Everyday care");
        assertThat(ready.draft().position()).isEqualTo(2);
        assertThat(ready.draft().categories()).hasSize(1);
        assertThat(ready.draft().categories().getFirst().category()).isEqualTo("Pain");
        assertThat(ready.draft().categories().getFirst().images().getFirst().id())
                .isNull();
        assertThat(ready.draft().categories().getFirst().images().get(1).id()).isEqualTo(IMAGE);
        assertThat(ready.draft().newImages()).isEqualTo(1);

        AppSectionRules.Prepared omitted = AppSectionRules.prepare(json.readTree(
                "{\"title\":\"Care\",\"categories\":[{\"category\":\"Pain\",\"images\":[{\"file\":true}]}]}"));
        assertThat(omitted.draft().position()).isZero();

        assertThat(AppSectionRules.prepare(json.readTree("{\"title\":\"Care\",\"position\":-1}"))
                        .problem())
                .isEqualTo("Position is not valid");
        assertThat(AppSectionRules.prepare(json.readTree(
                                "{\"title\":\"Care\",\"categories\":[{\"category\":\"Pain\",\"images\":[]}]}"))
                        .problem())
                .isEqualTo("Each category needs an image");
        assertThat(AppSectionRules.prepare(
                                json.readTree(
                                        "{\"title\":\"Care\",\"categories\":[{\"category\":\"Pain\",\"images\":[{\"file\":true}]},{\"category\":\"pain\",\"images\":[{\"file\":true}]}]}"))
                        .problem())
                .isEqualTo("Category is already listed");
        assertThat(AppSectionRules.prepare(
                                json.readTree(
                                        "{\"title\":\"Care\",\"categories\":[{\"category\":\"Pain\",\"images\":[{\"id\":\"not-an-id\"}]}]}"))
                        .problem())
                .isEqualTo("Category image is not valid");

        assertThat(AppSectionRules.imageProblem(null, true)).isEqualTo("Image is required");
        assertThat(AppSectionRules.imageProblem(null, false)).isNull();
        assertThat(AppSectionRules.fileProblem(null, true, "Banner")).isEqualTo("Banner is required");
        assertThat(AppSectionRules.imageProblem(new AppSectionRules.Upload("text/plain", new byte[] {1}), true))
                .isEqualTo("Image must be an image");
        assertThat(AppSectionRules.imageProblem(new AppSectionRules.Upload("image/jpg", new byte[] {1}), true))
                .isNull();
        assertThat(AppSectionRules.media("image/jpg")).isEqualTo("image/jpeg");
        byte[] huge = new byte[AppSectionRules.IMAGE_MAX + 1];
        assertThat(AppSectionRules.fileProblem(new AppSectionRules.Upload("image/png", huge), true, "Banner"))
                .isEqualTo("Banner is too large");
    }
}
