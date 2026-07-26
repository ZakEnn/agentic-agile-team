package com.agile.team.domain.port;

import java.util.List;

/**
 * A Jira issue as the Reviewer agent needs it.
 *
 * @param key                issue key, e.g. SCA-123
 * @param summary            issue title
 * @param description        full description
 * @param status             workflow status name
 * @param acceptanceCriteria criteria parsed from the description; empty when none
 *                           could be found, which is itself worth surfacing
 */
public record JiraIssueSnapshot(
        String key,
        String summary,
        String description,
        String status,
        List<String> acceptanceCriteria
) {
    public JiraIssueSnapshot {
        if (summary == null) summary = "";
        if (description == null) description = "";
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }

    public boolean hasAcceptanceCriteria() {
        return !acceptanceCriteria.isEmpty();
    }
}
