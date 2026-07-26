package com.orange.ai.reviewer_agent.domain.jira;

/**
 * Domain service interface for SonarQube operations
 */
public interface SonarQubeService {
    QualityGateStatus getQualityGateStatus(String projectKey);
    IssuesSummary getIssuesForMergeRequest(String projectKey, String branchName);
}
