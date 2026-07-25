package com.agile.team.domain.port;

import java.util.List;
import java.util.Optional;

public interface JiraPort {

    String createIssue(String projectKey, String summary, String description, String issueType);

    void updateIssueStatus(String issueKey, String status);

    Optional<String> getIssueStatus(String issueKey);

    List<String> getSprintIssues(String sprintId);

    void addComment(String issueKey, String comment);
}
