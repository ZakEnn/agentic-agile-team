package com.orange.ai.reviewer_agent.domain.jira;

/**
 * Domain service interface for Jira operations
 */
public interface JiraService {
    IssueDetails getIssueDetails(String issueKey);
}