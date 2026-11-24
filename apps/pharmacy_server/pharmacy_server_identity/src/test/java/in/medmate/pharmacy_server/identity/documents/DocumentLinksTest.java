package in.medmate.pharmacy_server.identity.documents;

import static org.assertj.core.api.Assertions.assertThat;

import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class DocumentLinksTest {

    @Test
    void localLinkIsTheDownloadRoute() {
        String pharmacyId = "11111111-1111-4111-8111-111111111111";
        String key = "app-uploads/medmate-server/pharmacy-documents/" + pharmacyId + "/gst";

        assertThat(new MemoryDocumentStore().url(key)).isEqualTo("/pharmacies/" + pharmacyId + "/documents/gst");
    }

    @Test
    void productionLinkIsAPresignedS3Get() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("AKIATESTEXAMPLE", "secretsecretsecretsecret"));
        try (S3Client client = S3Client.builder()
                        .region(Region.AP_SOUTH_1)
                        .credentialsProvider(credentials)
                        .build();
                S3Presigner presigner = S3Presigner.builder()
                        .region(Region.AP_SOUTH_1)
                        .credentialsProvider(credentials)
                        .build()) {
            String key = "app-uploads/medmate-server/pharmacy-documents/11111111-1111-4111-8111-111111111111/gst";
            String url = new S3DocumentStore(client, presigner, "medmate-repo").url(key);
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);

            assertThat(decoded).startsWith("https://medmate-repo.s3.ap-south-1.amazonaws.com/" + key);
            assertThat(decoded).contains("X-Amz-Expires=900");
            assertThat(url).contains("X-Amz-Signature=");
        }
    }

    @Test
    void garageLinkIsSignedForThePublicEndpoint() {
        MedmateIdentityProperties.Uploads uploads = new MedmateIdentityProperties.Uploads(
                "pharmacy_server",
                "app-uploads/medmate-server",
                "us-east-1",
                "http://storage:3900",
                "https://storage.medmate.in",
                "garage-access",
                "garage-secret-garage-secret",
                true);
        String key = "app-uploads/medmate-server/pharmacy-documents/11111111-1111-4111-8111-111111111111/gst";

        try (DocumentStore store = S3StorageClients.open(uploads)) {
            String url = store.url(key);
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);

            assertThat(decoded).startsWith("https://storage.medmate.in/pharmacy_server/" + key);
            assertThat(decoded).contains("X-Amz-Expires=900");
            assertThat(url).doesNotContain("storage:3900");
        }
    }
}
