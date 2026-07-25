package com.orange.ai.reviewer_agent.infrastructure.jira;

import com.orange.ai.reviewer_agent.domain.jira.IssueDetails;
import com.orange.ai.reviewer_agent.domain.jira.JiraService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

/**
 * Infrastructure implementation of Jira API client
 */
@Component
public class JiraClient implements JiraService {

    private final RestClient restClient;

    public JiraClient(JiraProperties properties) {
        String auth = Base64.getEncoder().encodeToString(
                (properties.email() + ":" + properties.apiToken()).getBytes()
        );
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + auth)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public IssueDetails getIssueDetails(String issueKey) {
        var response = restClient.get()
                .uri("/rest/api/3/issue/{issueKey}?fields=summary,description,issuetype,status,priority,assignee,customfield_10000", issueKey)
                .retrieve()
                .body(JiraIssueResponse.class);

        if (response == null) {
            throw new RuntimeException("Unable to fetch Jira issue: " + issueKey);
        }

        List<String> acceptanceCriteria = extractAcceptanceCriteria(response.fields().description());
        String assigneeName = response.fields().assignee() != null ?
                response.fields().assignee().displayName() : "Unassigned";

        return new IssueDetails(
                response.key(),
                response.fields().summary(),
                response.fields().description(),
                response.fields().issuetype().name(),
                response.fields().status().name(),
                response.fields().priority() != null ? response.fields().priority().name() : "None",
                acceptanceCriteria,
                assigneeName
        );
    }

    private List<String> extractAcceptanceCriteria(String description) {
        if (description == null || description.isEmpty()) {
            return List.of();
        }

        // Try to extract acceptance criteria from description
        // Common patterns: "Acceptance Criteria:", "AC:", "Given/When/Then"
        String[] lines = description.split("\n");
        List<String> criteria = new java.util.ArrayList<>();
        boolean inAcSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("(?i).*acceptance criteria.*")) {
                inAcSection = true;
                continue;
            }
            if (inAcSection && !trimmed.isEmpty()) {
                if (trimmed.startsWith("-") || trimmed.startsWith("*") ||
                    trimmed.matches("^\\d+\\..*") || trimmed.matches("(?i)^(given|when|then).*")) {
                    criteria.add(trimmed);
                } else if (trimmed.matches("(?i)^(description|notes|comments).*")) {
                    break; // End of AC section
                }
            }
        }

        return criteria.isEmpty() ? List.of("No explicit acceptance criteria found") : criteria;
    }

    // DTOs for Jira API responses
    record JiraIssueResponse(
            String key,
            Fields fields
    ) {
        record Fields(
                String summary,
                String description,
                IssueType issuetype,
                Status status,
                Priority priority,
                Assignee assignee
        ) {}

        record IssueType(String name) {}
        record Status(String name) {}
        record Priority(String name) {}
        record Assignee(String displayName) {}
    }
}
