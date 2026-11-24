package in.medmate.pharmacy_server.identity.config;

import in.medmate.pharmacy_server.identity.documents.DocumentStore;
import in.medmate.pharmacy_server.identity.documents.S3StorageClients;
import in.medmate.pharmacy_server.identity.mail.MailSender;
import in.medmate.pharmacy_server.identity.mail.RecordingMailSender;
import in.medmate.pharmacy_server.identity.mail.ResendMailSender;
import in.medmate.pharmacy_server.identity.security.JwtFilter;
import in.medmate.pharmacy_server.identity.security.PharmacyGateFilter;
import in.medmate.pharmacy_server.identity.session.AccessTokens;
import in.medmate.pharmacy_server.identity.session.AccountDirectory;
import in.medmate.pharmacy_server.identity.sms.HttpSmsSender;
import in.medmate.pharmacy_server.identity.sms.RecordingSmsSender;
import in.medmate.pharmacy_server.identity.sms.SmsSender;
import in.medmate.pharmacy_server.identity.sms.TelinfySmsSender;
import in.medmate.pharmacy_server.shared.error.ProblemResponses;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Profile({"local", "prod", "identity-it"})
@EnableConfigurationProperties(MedmateIdentityProperties.class)
@EnableTransactionManagement
public class IdentityConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AccessTokens accessTokens(MedmateIdentityProperties properties) {
        return new AccessTokens(properties.jwt().secret(), properties.jwt().accessSeconds());
    }

    @Bean
    @Primary
    SmsSender smsSender(MedmateIdentityProperties properties, RecordingSmsSender recording) {
        String apiKey = properties.sms() == null ? "" : properties.sms().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            return new TelinfySmsSender(apiKey);
        }
        String baseUrl = properties.sms() == null ? "" : properties.sms().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return recording;
        }
        return new HttpSmsSender(baseUrl);
    }

    @Bean
    @Primary
    MailSender mailSender(MedmateIdentityProperties properties, RecordingMailSender recording) {
        if (properties.mail() != null && "resend".equals(properties.mail().mode())) {
            return new ResendMailSender(
                    properties.mail().resendApiKey(), properties.mail().from());
        }
        return recording;
    }

    @Bean(destroyMethod = "close")
    DocumentStore documentStore(MedmateIdentityProperties properties) {
        return S3StorageClients.open(properties.uploads());
    }

    @Bean
    JwtFilter jwtFilter(AccessTokens tokens, AccountDirectory directory, Clock clock) {
        return new JwtFilter(tokens, directory, clock);
    }

    @Bean
    PharmacyGateFilter pharmacyGateFilter() {
        return new PharmacyGateFilter();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtFilter jwtFilter, PharmacyGateFilter pharmacyGate)
            throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/**",
                                "/auth/**",
                                "/support/stream",
                                "/api/search",
                                "/api/products",
                                "/api/products/**",
                                "/api/v1/leads",
                                "/api/v1/leads/**",
                                "/api/v1/catalog",
                                "/api/v1/catalog/**",
                                "/api/v1/inventory-prep",
                                "/api/v1/inventory-prep/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                                (request, response, exception) -> ProblemResponses.write(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        "Sign in is required"))
                        .accessDeniedHandler((request, response, exception) -> ProblemResponses.write(
                                request, response, HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(pharmacyGate, JwtFilter.class);
        return http.build();
    }
}
