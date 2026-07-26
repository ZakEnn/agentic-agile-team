package com.agile.team.infrastructure.adapter.gitlab;

import com.agile.team.domain.port.GitLabPort;
import com.agile.team.domain.port.MergeRequestSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real GitLab REST client, ported from the {@code reviewer-agent} project.
 * <p>
 * This replaces {@code infrastructure/adapter/mcp/GitLabAdapter}, which implemented
 * the same port by sending "Create a GitLab branch 'x' from 'main'" to a language
 * model and returning the reply as though it were the result of an API call — with
 * MCP disabled, so the model had no tools and could only fabricate.
 */
@Component
@EnableConfigurationProperties(GitLabProperties.class)
public class GitLabRestClient implements GitLabPort {

    private static final Logger log = LoggerFactory.getLogger(GitLabRestClient.class);

    /** Jira issue keys such as SCA-123. */
    private static final Pattern JIRA_KEY = Pattern.compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b");

    /**
     * Cap the diff handed to the model. A large MR can otherwise blow the context
     * window and the token budget in a single call.
     */
    private static final int MAX_DIFF_CHARS = 60_000;

    private final RestClient restClient;
    private final GitLabProperties properties;

    public GitLabRestClient(GitLabProperties properties) {
        this.properties = properties;
        // RestClient.builder() rather than an injected RestClient.Builder: Spring
        // Boot 4 with the webmvc starter does not auto-configure that bean, and this
        // matches the pattern ConfluenceRestClient already uses.
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("PRIVATE-TOKEN", properties.token() != null ? properties.token() : "")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<MergeRequestSnapshot> fetchMergeRequest(String projectId, String mergeRequestIid) {
        String encoded = encodeProjectId(projectId);
        try {
            GitLabDtos.MergeRequest mr = restClient.get()
                    .uri("/api/v4/projects/{project}/merge_requests/{iid}", encoded, mergeRequestIid)
                    .retrieve()
                    .body(GitLabDtos.MergeRequest.class);

            if (mr == null) {
                return Optional.empty();
            }

            GitLabDtos.MergeRequestChanges changes = restClient.get()
                    .uri("/api/v4/projects/{project}/merge_requests/{iid}/changes", encoded, mergeRequestIid)
                    .retrieve()
                    .body(GitLabDtos.MergeRequestChanges.class);

            return Optional.of(new MergeRequestSnapshot(
                    projectId,
                    mergeRequestIid,
                    mr.title(),
                    mr.description(),
                    mr.sourceBranch(),
                    mr.targetBranch(),
                    mr.authorName(),
                    extractJiraKey(mr.title(), mr.description()),
                    buildDiff(changes)));
        } catch (Exception e) {
            log.error("GitLab: failed to fetch MR {}!{}: {}", projectId, mergeRequestIid, e.getMessage());
            throw new GitLabException(
                    "Failed to fetch merge request " + projectId + "!" + mergeRequestIid, e);
        }
    }

    @Override
    public String createBranch(String projectId, String branchName, String sourceBranch) {
        restClient.post()
                .uri(uri -> uri.path("/api/v4/projects/{project}/repository/branches")
                        .queryParam("branch", branchName)
                        .queryParam("ref", sourceBranch)
                        .build(encodeProjectId(projectId)))
                .retrieve()
                .toBodilessEntity();
        log.info("GitLab: created branch {} from {} in {}", branchName, sourceBranch, projectId);
        return branchName;
    }

    @Override
    public String createMergeRequest(String projectId, String sourceBranch, String targetBranch,
                                     String title, String description) {
        GitLabDtos.MergeRequest created = restClient.post()
                .uri("/api/v4/projects/{project}/merge_requests", encodeProjectId(projectId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "source_branch", sourceBranch,
                        "target_branch", targetBranch,
                        "title", title,
                        "description", description != null ? description : ""))
                .retrieve()
                .body(GitLabDtos.MergeRequest.class);

        if (created == null || created.iid() == null) {
            throw new GitLabException("GitLab returned no merge request id for " + projectId, null);
        }
        log.info("GitLab: created MR !{} in {}", created.iid(), projectId);
        return String.valueOf(created.iid());
    }

    @Override
    public Optional<String> getMergeRequestStatus(String projectId, String mergeRequestId) {
        GitLabDtos.MergeRequest mr = restClient.get()
                .uri("/api/v4/projects/{project}/merge_requests/{iid}", encodeProjectId(projectId), mergeRequestId)
                .retrieve()
                .body(GitLabDtos.MergeRequest.class);
        return Optional.ofNullable(mr).map(GitLabDtos.MergeRequest::state);
    }

    @Override
    public void mergeMergeRequest(String projectId, String mergeRequestId) {
        restClient.put()
                .uri("/api/v4/projects/{project}/merge_requests/{iid}/merge",
                        encodeProjectId(projectId), mergeRequestId)
                .retrieve()
                .toBodilessEntity();
        log.info("GitLab: merged MR !{} in {}", mergeRequestId, projectId);
    }

    @Override
    public void addMergeRequestComment(String projectId, String mergeRequestId, String comment) {
        restClient.post()
                .uri("/api/v4/projects/{project}/merge_requests/{iid}/notes",
                        encodeProjectId(projectId), mergeRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", comment))
                .retrieve()
                .toBodilessEntity();
        log.info("GitLab: posted review comment to MR !{} in {}", mergeRequestId, projectId);
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** Project paths must be URL-encoded for the GitLab API; numeric ids must not. */
    static String encodeProjectId(String projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (projectId.chars().allMatch(Character::isDigit)) {
            return projectId;
        }
        return URLEncoder.encode(projectId, StandardCharsets.UTF_8);
    }

    static String extractJiraKey(String title, String description) {
        Matcher matcher = JIRA_KEY.matcher(title != null ? title : "");
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (description != null) {
            matcher = JIRA_KEY.matcher(description);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String buildDiff(GitLabDtos.MergeRequestChanges changes) {
        if (changes == null || changes.changes() == null || changes.changes().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        List<GitLabDtos.MergeRequestChanges.Change> list = changes.changes();
        for (GitLabDtos.MergeRequestChanges.Change change : list) {
            if (builder.length() >= MAX_DIFF_CHARS) {
                builder.append("\n... diff truncated at ").append(MAX_DIFF_CHARS)
                       .append(" characters; ").append(list.size())
                       .append(" file(s) changed in total ...\n");
                break;
            }
            builder.append("diff --git a/").append(change.oldPath())
                   .append(" b/").append(change.newPath()).append('\n');
            if (change.diff() != null) {
                builder.append(change.diff()).append('\n');
            }
        }
        return builder.toString();
    }

    /** Wraps transport failures so callers can distinguish them from bad input. */
    public static class GitLabException extends RuntimeException {
        public GitLabException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
