package com.agile.team.domain.port;

import java.util.List;
import java.util.Optional;

public interface JiraPort {

    /**
     * Fetch an issue's details, including any acceptance criteria parsed from its
     * description. Used by the Reviewer agent to judge a change against what was
     * actually asked for.
     *
     * @return the issue, or empty when it does not exist or Jira is unavailable
     */
    Optional<JiraIssueSnapshot> fetchIssue(String issueKey);

    String createIssue(String projectKey, String summary, String description, String issueType);

    void updateIssueStatus(String issueKey, String status);

    Optional<String> getIssueStatus(String issueKey);

    List<String> getSprintIssues(String sprintId);

    void addComment(String issueKey, String comment);
}
