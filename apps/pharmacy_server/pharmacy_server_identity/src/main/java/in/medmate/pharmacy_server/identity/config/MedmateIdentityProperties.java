package in.medmate.pharmacy_server.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medmate")
public record MedmateIdentityProperties(Jwt jwt, String adminBootstrapToken, Sms sms, Mail mail, Uploads uploads) {

    public record Jwt(String secret, long accessSeconds, long refreshSeconds) {}

    public record Sms(String baseUrl, String apiKey) {}

    public record Mail(String mode, String from, String resendApiKey) {}

    public record Uploads(
            String bucket,
            String prefix,
            String region,
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            Boolean forcePathStyle) {}

    public String uploadPrefix() {
        if (uploads == null || uploads.prefix() == null || uploads.prefix().isBlank()) {
            return "app-uploads/medmate-server";
        }
        return uploads.prefix();
    }
}
