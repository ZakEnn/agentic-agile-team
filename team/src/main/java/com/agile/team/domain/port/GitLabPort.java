package com.agile.team.domain.port;

import java.util.Optional;

public interface GitLabPort {

    /**
     * Fetch a merge request with its diff.
     * <p>
     * Added in M2 to carry the capability ported from the {@code reviewer-agent}
     * project, whose GitLab client was the only working toolchain integration in
     * either codebase.
     *
     * @return the snapshot, or empty when the MR does not exist
     */
    Optional<MergeRequestSnapshot> fetchMergeRequest(String projectId, String mergeRequestIid);

    String createBranch(String projectId, String branchName, String sourceBranch);

    String createMergeRequest(String projectId, String sourceBranch, String targetBranch, String title, String description);

    Optional<String> getMergeRequestStatus(String projectId, String mergeRequestId);

    void mergeMergeRequest(String projectId, String mergeRequestId);

    void addMergeRequestComment(String projectId, String mergeRequestId, String comment);
}
