package com.orange.ai.reviewer_agent.infrastructure.gitlab;

import com.orange.ai.reviewer_agent.domain.gitlab.GitLabService;
import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "gitlab")
public record GitLabProperties(
        String baseUrl,
        String token
) {
    /**
     * Infrastructure implementation of GitLab API client
     */
    @Component
    public static class GitLabClient implements GitLabService {

        private final RestClient restClient;
        private static final Pattern JIRA_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

        public GitLabClient(GitLabProperties properties) {
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("PRIVATE-TOKEN", properties.token())
                    .build();
        }

        @Override
        public MergeRequestInfo fetchMergeRequestInfo(String projectId, String mergeRequestIid) {
            // Fetch MR details
            var mrDetails = restClient.get()
                    .uri("/api/v4/projects/{projectId}/merge_requests/{iid}", projectId, mergeRequestIid)
                    .retrieve()
                    .body(GitLabMergeRequest.class);

            // Fetch MR changes (diff)
            var changes = restClient.get()
                    .uri("/api/v4/projects/{projectId}/merge_requests/{iid}/changes", projectId, mergeRequestIid)
                    .retrieve()
                    .body(GitLabMergeRequestChanges.class);

            String jiraTicket = extractJiraTicket(mrDetails.title(), mrDetails.description());
            String diff = buildDiff(changes);

            return new MergeRequestInfo(
                    mrDetails.title(),
                    mrDetails.description(),
                    mrDetails.sourceBranch(),
                    mrDetails.targetBranch(),
                    mrDetails.author().name(),
                    jiraTicket,
                    diff
            );
        }

        @Override
        public void postComment(String projectId, String mergeRequestIid, String comment) {
            restClient.post()
                    .uri("/api/v4/projects/{projectId}/merge_requests/{iid}/notes", projectId, mergeRequestIid)
                    .body(Map.of("body", comment))
                    .retrieve()
                    .toBodilessEntity();
        }

        private String extractJiraTicket(String title, String description) {
            // Try to extract from title first
            Matcher matcher = JIRA_PATTERN.matcher(title);
            if (matcher.find()) {
                return matcher.group(1);
            }

            // Try description if not found in title
            if (description != null) {
                matcher = JIRA_PATTERN.matcher(description);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            return null;
        }

        private String buildDiff(GitLabMergeRequestChanges changes) {
            if (changes == null || changes.changes() == null) {
                return "";
            }

            StringBuilder diffBuilder = new StringBuilder();
            for (var change : changes.changes()) {
                diffBuilder.append("diff --git a/")
                        .append(change.oldPath())
                        .append(" b/")
                        .append(change.newPath())
                        .append("\n");
                diffBuilder.append(change.diff()).append("\n");
            }

            return diffBuilder.toString();
        }

        // DTOs for GitLab API responses
        record GitLabMergeRequest(
                String title,
                String description,
                String sourceBranch,
                String targetBranch,
                Author author
        ) {
            record Author(String name) {}
        }

        record GitLabMergeRequestChanges(
                java.util.List<Change> changes
        ) {
            record Change(
                    String oldPath,
                    String newPath,
                    String diff
            ) {}
        }
    }
}
