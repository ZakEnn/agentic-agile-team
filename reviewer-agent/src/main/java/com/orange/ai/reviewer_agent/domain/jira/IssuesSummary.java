package com.orange.ai.reviewer_agent.domain.jira;

import java.util.List;

/**
 * Represents a summary of SonarQube issues
 */
public record IssuesSummary(
        int totalIssues,
        List<Issue> issues
) {
    public record Issue(
            String key,
            String severity,
            String type,
            String component,
            Integer line,
            String message,
            String rule
    ) {}
}


