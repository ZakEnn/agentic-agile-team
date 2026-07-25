package com.orange.ai.reviewer_agent.domain.gitlab;

/**
 * Domain service interface for GitLab operations
 */
public interface GitLabService {
    MergeRequestInfo fetchMergeRequestInfo(String projectId, String mergeRequestIid);
    void postComment(String projectId, String mergeRequestIid, String comment);
}

