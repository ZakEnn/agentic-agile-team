package com.agile.team.infrastructure.adapter.jira;

import com.agile.team.domain.port.JiraIssueSnapshot;
import com.agile.team.domain.port.JiraPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real Jira REST client, ported from {@code reviewer-agent}.
 * <p>
 * Replaces {@code infrastructure/adapter/mcp/JiraAdapter}, which asked a language
 * model to "create a Jira issue" in prose and returned the reply as the issue key.
 */
@Component
@EnableConfigurationProperties(JiraRestClient.JiraProperties.class)
public class JiraRestClient implements JiraPort {

    private static final Logger log = LoggerFactory.getLogger(JiraRestClient.class);

    private final RestClient restClient;
    private final JiraProperties properties;

    public JiraRestClient(JiraProperties properties) {
        this.properties = properties;
        String auth = Base64.getEncoder().encodeToString(
                ((properties.email() != null ? properties.email() : "") + ":"
                        + (properties.apiToken() != null ? properties.apiToken() : ""))
                        .getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + auth)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<JiraIssueSnapshot> fetchIssue(String issueKey) {
        if (!properties.isConfigured()) {
            log.warn("Jira is not configured; cannot fetch {}", issueKey);
            return Optional.empty();
        }
        try {
            IssueResponse response = restClient.get()
                    .uri("/rest/api/2/issue/{key}?fields=summary,description,status", issueKey)
                    .retrieve()
                    .body(IssueResponse.class);

            if (response == null || response.fields() == null) {
                return Optional.empty();
            }
            IssueResponse.Fields fields = response.fields();
            return Optional.of(new JiraIssueSnapshot(
                    response.key(),
                    fields.summary(),
                    fields.description(),
                    fields.status() != null ? fields.status().name() : "UNKNOWN",
                    extractAcceptanceCriteria(fields.description())));
        } catch (Exception e) {
            // Jira context is enriching, not load-bearing: the reviewer can still
            // judge the diff without it. Degrade rather than fail the stage.
            log.warn("Jira: failed to fetch {} ({}); continuing without issue context",
                    issueKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String createIssue(String projectKey, String summary, String description, String issueType) {
        IssueResponse created = restClient.post()
                .uri("/rest/api/2/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("fields", Map.of(
                        "project", Map.of("key", projectKey),
                        "summary", summary,
                        "description", description != null ? description : "",
                        "issuetype", Map.of("name", issueType != null ? issueType : "Story"))))
                .retrieve()
                .body(IssueResponse.class);

        if (created == null || created.key() == null) {
            throw new IllegalStateException("Jira returned no issue key for project " + projectKey);
        }
        log.info("Jira: created issue {}", created.key());
        return created.key();
    }

    @Override
    public void updateIssueStatus(String issueKey, String status) {
        // Jira status changes go through transitions, which are workflow-specific.
        // Left unimplemented rather than faked: a wrong transition id silently moves
        // an issue to the wrong state, which is worse than not moving it.
        throw new UnsupportedOperationException(
                "Jira transitions are workflow-specific and must be configured per project; "
                        + "see IMPLEMENTATION_LOG.md (M2 known limitation)");
    }

    @Override
    public Optional<String> getIssueStatus(String issueKey) {
        return fetchIssue(issueKey).map(JiraIssueSnapshot::status);
    }

    @Override
    public List<String> getSprintIssues(String sprintId) {
        // Requires the Jira Agile API, which is not needed by any current stage.
        return List.of();
    }

    @Override
    public void addComment(String issueKey, String comment) {
        restClient.post()
                .uri("/rest/api/2/issue/{key}/comment", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", comment))
                .retrieve()
                .toBodilessEntity();
        log.info("Jira: commented on {}", issueKey);
    }

    /**
     * Pull acceptance criteria out of a free-text description.
     * <p>
     * Heuristic by necessity — Jira has no standard field for this. Kept from the
     * ported implementation, with the bullet/numbered/Given-When-Then patterns that
     * cover how the criteria are actually written in practice. Returns empty rather
     * than a placeholder string when nothing matches, so callers can tell the
     * difference between "no criteria" and "a criterion that says none were found"
     * (the original returned the latter, which then flowed into prompts as if it
     * were a real requirement).
     */
    static List<String> extractAcceptanceCriteria(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<String> criteria = new ArrayList<>();
        boolean inSection = false;

        for (String rawLine : description.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.matches("(?i).*acceptance\\s+criteria.*")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("(?i)^(description|notes|comments|technical details)\\b.*")) {
                break;
            }
            if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•")
                    || line.matches("^\\d+[.)].*")
                    || line.matches("(?i)^(given|when|then|and)\\b.*")) {
                criteria.add(line.replaceFirst("^[-*•]\\s*", "").trim());
            }
        }
        return List.copyOf(criteria);
    }

    record IssueResponse(
            @JsonProperty("key") String key,
            @JsonProperty("fields") Fields fields
    ) {
        record Fields(
                @JsonProperty("summary") String summary,
                @JsonProperty("description") String description,
                @JsonProperty("status") Status status
        ) {
        }

        record Status(@JsonProperty("name") String name) {
        }
    }

    @ConfigurationProperties(prefix = "jira")
    public record JiraProperties(String baseUrl, String email, String apiToken) {
        public JiraProperties {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://jira.invalid";
            }
        }

        public boolean isConfigured() {
            return apiToken != null && !apiToken.isBlank() && !baseUrl.contains("invalid");
        }
    }
}
