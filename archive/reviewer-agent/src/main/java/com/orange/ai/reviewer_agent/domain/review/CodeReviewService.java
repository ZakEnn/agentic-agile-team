package com.orange.ai.reviewer_agent.domain.review;

import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;

/**
 * Domain service for code review operations
 */
public interface CodeReviewService {
    ReviewComment performReview(MergeRequestInfo mergeRequestInfo);
}
