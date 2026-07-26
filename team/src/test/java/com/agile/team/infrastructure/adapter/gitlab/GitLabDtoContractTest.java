package com.agile.team.infrastructure.adapter.gitlab;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the GitLab wire contract against recorded API payloads.
 * <p>
 * This test exists because of a specific, silent defect in the code ported from
 * {@code reviewer-agent}: its DTOs used camelCase field names with no
 * {@code @JsonProperty} and no naming strategy, while GitLab returns snake_case.
 * Jackson bound {@code source_branch}, {@code target_branch}, {@code old_path} and
 * {@code new_path} to null, and because Spring Boot disables
 * FAIL_ON_UNKNOWN_PROPERTIES nothing ever threw. The webhook handler then
 * dereferenced a null {@code object_attributes} on every real delivery.
 * <p>
 * A defect that produces nulls rather than exceptions is invisible without a test
 * like this one. Any future refactor that drops an annotation fails here.
 */
class GitLabDtoContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldBindMergeRequestIncludingSnakeCaseBranchFields() {
        GitLabDtos.MergeRequest mr = read("gitlab-merge-request.json", GitLabDtos.MergeRequest.class);

        assertEquals(42, mr.iid());
        assertEquals("SCA-1234 Add retry with exponential backoff to the SFTP poller", mr.title());
        // The fields that were silently null before.
        assertEquals("feature/SCA-1234-sftp-retry", mr.sourceBranch(),
                "source_branch must bind — this was null in the ported implementation");
        assertEquals("main", mr.targetBranch(),
                "target_branch must bind — this was null in the ported implementation");
        assertEquals("A Developer", mr.authorName());
        assertEquals("opened", mr.state());
    }

    @Test
    void shouldBindChangesIncludingSnakeCasePathFields() {
        GitLabDtos.MergeRequestChanges changes =
                read("gitlab-merge-request-changes.json", GitLabDtos.MergeRequestChanges.class);

        assertEquals(2, changes.changes().size());
        GitLabDtos.MergeRequestChanges.Change first = changes.changes().get(0);
        assertEquals("src/main/java/com/orange/epe/SftpPoller.java", first.oldPath());
        assertEquals("src/main/java/com/orange/epe/SftpPoller.java", first.newPath());
        assertNotNull(first.diff());
        assertTrue(first.diff().contains("SocketTimeoutException"));
        assertEquals(Boolean.TRUE, changes.changes().get(1).newFile());
    }

    @Test
    void shouldBindWebhookPayloadThatPreviouslyNullPointered() {
        GitLabDtos.WebhookPayload payload =
                read("gitlab-webhook-merge-request.json", GitLabDtos.WebhookPayload.class);

        assertEquals("merge_request", payload.objectKind());
        assertNotNull(payload.objectAttributes(),
                "object_attributes must bind — dereferencing this null was the webhook crash");
        assertEquals("open", payload.objectAttributes().action());
        assertEquals(42, payload.objectAttributes().iid());
        assertNotNull(payload.project());
        assertEquals("epe/epe-rating-ftth-passive", payload.project().pathWithNamespace());
    }

    @Test
    void shouldRecogniseReviewableEvents() {
        GitLabDtos.WebhookPayload payload =
                read("gitlab-webhook-merge-request.json", GitLabDtos.WebhookPayload.class);
        assertTrue(payload.isReviewableMergeRequestEvent());
    }

    @Test
    void shouldIgnoreNonMergeRequestEvents() {
        GitLabDtos.WebhookPayload pipeline = mapper.readValue(
                "{\"object_kind\":\"pipeline\"}", GitLabDtos.WebhookPayload.class);
        assertFalse(pipeline.isReviewableMergeRequestEvent());
    }

    @Test
    void shouldIgnoreMergeRequestClosedAndMergedActions() {
        // Reviewing a merged MR wastes tokens and posts a comment nobody reads.
        for (String action : new String[]{"close", "merge", "reopen", "approved"}) {
            GitLabDtos.WebhookPayload payload = mapper.readValue(
                    "{\"object_kind\":\"merge_request\",\"object_attributes\":{\"action\":\"" + action + "\"}}",
                    GitLabDtos.WebhookPayload.class);
            assertFalse(payload.isReviewableMergeRequestEvent(),
                    "action '" + action + "' should not trigger a review");
        }
    }

    @Test
    void shouldBuildAUnifiedDiffFromChanges() {
        GitLabDtos.MergeRequestChanges changes =
                read("gitlab-merge-request-changes.json", GitLabDtos.MergeRequestChanges.class);

        String diff = GitLabRestClient.buildDiff(changes);

        assertTrue(diff.contains("diff --git a/src/main/java/com/orange/epe/SftpPoller.java"));
        assertTrue(diff.contains("diff --git a/src/test/java/com/orange/epe/SftpPollerTest.java"));
        assertTrue(diff.contains("backoff(attempt)"));
    }

    @Test
    void shouldExtractJiraKeyFromTitleThenDescription() {
        assertEquals("SCA-1234", GitLabRestClient.extractJiraKey("SCA-1234 do a thing", null));
        assertEquals("ABC-7", GitLabRestClient.extractJiraKey("no key here", "relates to ABC-7"));
        assertNull(GitLabRestClient.extractJiraKey("no key", "none either"));
        // Must not match lowercase or bare numbers.
        assertNull(GitLabRestClient.extractJiraKey("fix-123 lowercase", null));
    }

    @Test
    void shouldUrlEncodeProjectPathsButNotNumericIds() {
        assertEquals("epe%2Fepe-rating-ftth-passive",
                GitLabRestClient.encodeProjectId("epe/epe-rating-ftth-passive"));
        assertEquals("278964", GitLabRestClient.encodeProjectId("278964"));
    }

    private <T> T read(String fixture, Class<T> type) {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            assertNotNull(in, "fixture not found: " + fixture);
            return mapper.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            throw new AssertionError("Failed to read fixture " + fixture, e);
        }
    }
}
