package com.agile.team.infrastructure.adapter.sonarqube;

import com.agile.team.domain.port.SonarQubePort;
import com.agile.team.domain.review.CodeQualityScore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Real SonarQube REST client, ported from {@code reviewer-agent}.
 * <p>
 * The important behaviour is what happens when it <em>cannot</em> answer. Every
 * failure path returns {@link CodeQualityScore#unknown()}, which
 * {@code ReviewGate} treats as a failure. The original reviewer handler took the
 * opposite approach — a hardcoded {@code passing(80)} "since we don't want to block
 * on unavailable SonarQube" — which converted an outage into an approval.
 */
@Component
@EnableConfigurationProperties(SonarQubeRestClient.SonarQubeProperties.class)
public class SonarQubeRestClient implements SonarQubePort {

    private static final Logger log = LoggerFactory.getLogger(SonarQubeRestClient.class);

    private final RestClient restClient;
    private final SonarQubeProperties properties;

    public SonarQubeRestClient(SonarQubeProperties properties) {
        this.properties = properties;
        // SonarQube uses the token as the basic-auth username with an empty password.
        String auth = Base64.getEncoder().encodeToString(
                ((properties.token() != null ? properties.token() : "") + ":")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + auth)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<CodeQualityScore> getQualityGateStatus(String projectKey) {
        if (!properties.isConfigured()) {
            log.warn("SonarQube is not configured; quality gate is UNKNOWN for {}", projectKey);
            return Optional.of(CodeQualityScore.unknown());
        }
        try {
            QualityGateResponse response = restClient.get()
                    .uri(uri -> uri.path("/api/qualitygates/project_status")
                            .queryParam("projectKey", projectKey)
                            .build())
                    .retrieve()
                    .body(QualityGateResponse.class);

            if (response == null || response.projectStatus() == null
                    || response.projectStatus().status() == null) {
                log.warn("SonarQube returned no project status for {}; treating as UNKNOWN", projectKey);
                return Optional.of(CodeQualityScore.unknown());
            }
            return Optional.of(toScore(response.projectStatus()));
        } catch (Exception e) {
            // Deliberately not rethrown: an unreachable SonarQube is a legitimate
            // operational state. It just must never look like a pass.
            log.error("SonarQube unreachable for {} ({}); quality gate is UNKNOWN",
                    projectKey, e.getMessage());
            return Optional.of(CodeQualityScore.unknown());
        }
    }

    @Override
    public Optional<CodeQualityScore> analyzeProject(String projectKey) {
        return getQualityGateStatus(projectKey);
    }

    /**
     * Translate SonarQube's gate result into a score.
     * <p>
     * The numeric score is derived from the proportion of conditions met, purely so
     * dashboards have a trend line. The pass/fail decision comes from SonarQube's own
     * {@code status}, never from the number — the original code did the reverse,
     * scraping "the first 0-100 number it finds" out of prose.
     */
    static CodeQualityScore toScore(ProjectStatus status) {
        List<Condition> conditions = status.conditions() != null ? status.conditions() : List.of();
        long met = conditions.stream().filter(c -> "OK".equalsIgnoreCase(c.status())).count();
        double ratio = conditions.isEmpty() ? 100.0 : (100.0 * met / conditions.size());

        return switch (status.status().toUpperCase()) {
            case "OK", "PASSED" -> CodeQualityScore.passing(ratio);
            case "ERROR", "FAILED", "WARN" -> CodeQualityScore.failing(ratio);
            // "NONE" means no analysis exists for this project. Not a pass.
            default -> CodeQualityScore.unknown();
        };
    }

    record QualityGateResponse(@JsonProperty("projectStatus") ProjectStatus projectStatus) {
    }

    record ProjectStatus(
            @JsonProperty("status") String status,
            @JsonProperty("conditions") List<Condition> conditions
    ) {
    }

    record Condition(
            @JsonProperty("status") String status,
            @JsonProperty("metricKey") String metricKey,
            @JsonProperty("comparator") String comparator,
            @JsonProperty("errorThreshold") String errorThreshold,
            @JsonProperty("actualValue") String actualValue
    ) {
    }

    @ConfigurationProperties(prefix = "sonarqube")
    public record SonarQubeProperties(String baseUrl, String token) {
        public SonarQubeProperties {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://sonarqube.invalid";
            }
        }

        public boolean isConfigured() {
            return token != null && !token.isBlank()
                    && !baseUrl.contains("invalid");
        }
    }
}
