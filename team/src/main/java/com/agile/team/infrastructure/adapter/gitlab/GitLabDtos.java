package com.agile.team.infrastructure.adapter.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire types for the GitLab REST API.
 * <p>
 * <strong>Every snake_case field carries an explicit {@link JsonProperty}.</strong>
 * This is the fix for the defect the ported {@code reviewer-agent} code shipped
 * with: its DTOs declared {@code sourceBranch}, {@code targetBranch}, {@code oldPath}
 * and {@code newPath} with no annotation and no naming strategy configured, while
 * GitLab returns {@code source_branch}, {@code target_branch}, {@code old_path} and
 * {@code new_path}. Jackson silently bound them to null — and because Spring Boot
 * disables FAIL_ON_UNKNOWN_PROPERTIES, nothing ever threw. The reviewer would have
 * analysed a diff with no branch names, and the webhook path
 * NullPointer-ed on every real GitLab call.
 * <p>
 * Explicit annotations were chosen over a global {@code SNAKE_CASE} naming strategy
 * because the strategy would apply to every DTO in the application, including the
 * REST API this service exposes — a silent, wide-reaching behaviour change to fix a
 * local problem. Annotations keep the contract visible at the point it matters, and
 * {@code GitLabDtoContractTest} pins it against recorded payloads.
 */
public final class GitLabDtos {

    private GitLabDtos() {
    }

    public record MergeRequest(
            @JsonProperty("iid") Integer iid,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("source_branch") String sourceBranch,
            @JsonProperty("target_branch") String targetBranch,
            @JsonProperty("web_url") String webUrl,
            @JsonProperty("state") String state,
            @JsonProperty("author") Author author
    ) {
        public record Author(@JsonProperty("name") String name) {
        }

        public String authorName() {
            return author != null ? author.name() : null;
        }
    }

    public record MergeRequestChanges(
            @JsonProperty("changes") List<Change> changes
    ) {
        public record Change(
                @JsonProperty("old_path") String oldPath,
                @JsonProperty("new_path") String newPath,
                @JsonProperty("diff") String diff,
                @JsonProperty("new_file") Boolean newFile,
                @JsonProperty("deleted_file") Boolean deletedFile
        ) {
        }
    }

    /** GitLab webhook payload for merge request events. */
    public record WebhookPayload(
            @JsonProperty("object_kind") String objectKind,
            @JsonProperty("object_attributes") ObjectAttributes objectAttributes,
            @JsonProperty("project") Project project
    ) {
        public record ObjectAttributes(
                @JsonProperty("iid") Integer iid,
                @JsonProperty("action") String action,
                @JsonProperty("url") String url,
                @JsonProperty("source_branch") String sourceBranch,
                @JsonProperty("target_branch") String targetBranch
        ) {
        }

        public record Project(
                @JsonProperty("id") Long id,
                @JsonProperty("path_with_namespace") String pathWithNamespace
        ) {
        }

        /** True for the events worth reviewing: a newly opened or updated MR. */
        public boolean isReviewableMergeRequestEvent() {
            if (!"merge_request".equals(objectKind) || objectAttributes == null) {
                return false;
            }
            String action = objectAttributes.action();
            return "open".equals(action) || "opened".equals(action) || "update".equals(action);
        }
    }
}
