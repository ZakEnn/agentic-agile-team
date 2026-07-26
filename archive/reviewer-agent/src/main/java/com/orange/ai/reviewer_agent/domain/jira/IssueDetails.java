package com.orange.ai.reviewer_agent.domain.jira;

import java.util.List;

/**
 * Represents Jira issue details
 */
public record IssueDetails(
        String key,
        String summary,
        String description,
        String issueType,
        String status,
        String priority,
        List<String> acceptanceCriteria,
        String assignee
) {
}
