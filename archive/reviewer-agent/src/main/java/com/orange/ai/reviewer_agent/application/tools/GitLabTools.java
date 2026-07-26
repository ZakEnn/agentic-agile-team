package com.orange.ai.reviewer_agent.application.tools;

import com.orange.ai.reviewer_agent.domain.gitlab.GitLabService;
import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring AI Tool for fetching GitLab Merge Request information
 * This function will be available to the LLM through function calling
 */
@Component
public class GitLabTools {

    private final GitLabService gitLabService;
    private static final Pattern MR_URI_PATTERN = Pattern.compile(
            "(?:https?://)?(?:[^/]+)/([^/]+/[^/]+)/-/merge_requests/(\\d+)"
    );

    public GitLabTools(GitLabService gitLabService) {
        this.gitLabService = gitLabService;
    }

    @Tool(description = "Fetches merge request information from GitLab including title, description, " +
            "source branch, target branch, author name, associated Jira ticket, and code diff. " +
            "Use this when you need to analyze a GitLab merge request.")
    public MergeRequestInfo fetchMergeRequestInfo(String mrUri) {
        var parsed = parseMergeRequestUri(mrUri);
        return gitLabService.fetchMergeRequestInfo(parsed.projectId(), parsed.mergeRequestIid());
    }

    @Tool(description = "Posts a comment to a GitLab merge request. Use this to provide code review feedback.")
    public String postMergeRequestComment(String mrUri, String comment) {
        var parsed = parseMergeRequestUri(mrUri);
        gitLabService.postComment(parsed.projectId(), parsed.mergeRequestIid(), comment);
        return "Comment posted successfully to merge request " + parsed.mergeRequestIid();
    }

    private ParsedMrUri parseMergeRequestUri(String mrUri) {
        Matcher matcher = MR_URI_PATTERN.matcher(mrUri);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid merge request URI format: " + mrUri);
        }

        String projectId = matcher.group(1).replace("/", "%2F"); // URL encode the project path
        String mergeRequestIid = matcher.group(2);

        return new ParsedMrUri(projectId, mergeRequestIid);
    }

    private record ParsedMrUri(String projectId, String mergeRequestIid) {}
}

