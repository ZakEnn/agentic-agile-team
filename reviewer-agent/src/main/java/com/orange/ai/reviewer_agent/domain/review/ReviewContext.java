package com.orange.ai.reviewer_agent.domain.review;

import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import com.orange.ai.reviewer_agent.domain.jira.IssueDetails;
import com.orange.ai.reviewer_agent.domain.jira.IssuesSummary;
import com.orange.ai.reviewer_agent.domain.jira.QualityGateStatus;

public record ReviewContext(MergeRequestInfo mergeRequest,
                            IssueDetails jiraIssue,
                            QualityGateStatus qualityGate,
                            IssuesSummary issuesSummary) {
}