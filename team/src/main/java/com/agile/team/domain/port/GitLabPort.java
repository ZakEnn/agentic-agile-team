package com.agile.team.domain.port;

import java.util.Optional;

public interface GitLabPort {

    String createBranch(String projectId, String branchName, String sourceBranch);

    String createMergeRequest(String projectId, String sourceBranch, String targetBranch, String title, String description);

    Optional<String> getMergeRequestStatus(String projectId, String mergeRequestId);

    void mergeMergeRequest(String projectId, String mergeRequestId);

    void addMergeRequestComment(String projectId, String mergeRequestId, String comment);
}
