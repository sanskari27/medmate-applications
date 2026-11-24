package in.medmate.pharmacy_server.identity.documents;

import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class S3StorageClients {

    private S3StorageClients() {}

    public static DocumentStore open(MedmateIdentityProperties.Uploads uploads) {
        String bucket = uploads == null ? "" : uploads.bucket();
        if (blank(bucket)) {
            return new MemoryDocumentStore();
        }
        String region = blank(uploads.region()) ? "us-east-1" : uploads.region();
        Region awsRegion = Region.of(region);
        boolean customEndpoint = !blank(uploads.endpoint());
        boolean pathStyle = uploads.forcePathStyle() == null ? customEndpoint : uploads.forcePathStyle();
        S3Configuration configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .chunkedEncodingEnabled(!customEndpoint)
                .build();
        S3ClientBuilder client = S3Client.builder().region(awsRegion).serviceConfiguration(configuration);
        S3Presigner.Builder presigner = S3Presigner.builder().region(awsRegion).serviceConfiguration(configuration);
        if (!blank(uploads.accessKey()) && !blank(uploads.secretKey())) {
            StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(uploads.accessKey(), uploads.secretKey()));
            client.credentialsProvider(credentials);
            presigner.credentialsProvider(credentials);
        }
        if (customEndpoint) {
            client.endpointOverride(URI.create(stripSlash(uploads.endpoint())))
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        }
        String publicEndpoint = blank(uploads.publicEndpoint()) ? uploads.endpoint() : uploads.publicEndpoint();
        if (!blank(publicEndpoint)) {
            presigner.endpointOverride(URI.create(stripSlash(publicEndpoint)));
        }
        return new S3DocumentStore(client.build(), presigner.build(), bucket);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripSlash(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }
}
