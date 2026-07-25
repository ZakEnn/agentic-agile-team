package com.orange.ai.reviewer_agent.domain.gitlab;

public record MergeRequestInfo(String title,
                               String description,
                               String sourceBranch,
                               String targetBranch,
                               String authorName,
                               String jiraTicket,
                               String diff) {
}
