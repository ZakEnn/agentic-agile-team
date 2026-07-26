package com.agile.team.domain.port;

/**
 * Everything the Reviewer agent needs about a merge request, fetched deterministically.
 *
 * @param projectId    project path or numeric id
 * @param iid          merge request internal id
 * @param title        MR title
 * @param description  MR description
 * @param sourceBranch source branch name
 * @param targetBranch target branch name
 * @param authorName   who opened it
 * @param jiraKey      issue key extracted from title or description, may be null
 * @param diff         unified diff of the change
 */
public record MergeRequestSnapshot(
        String projectId,
        String iid,
        String title,
        String description,
        String sourceBranch,
        String targetBranch,
        String authorName,
        String jiraKey,
        String diff
) {
    public MergeRequestSnapshot {
        if (title == null) title = "";
        if (description == null) description = "";
        if (diff == null) diff = "";
    }

    public boolean hasDiff() {
        return !diff.isBlank();
    }

    public boolean hasJiraKey() {
        return jiraKey != null && !jiraKey.isBlank();
    }
}
