package com.orange.ai.reviewer_agent.application.review;

import com.orange.ai.reviewer_agent.application.tools.GitLabTools;
import com.orange.ai.reviewer_agent.application.tools.JiraTools;
import com.orange.ai.reviewer_agent.application.tools.SonarQubeTools;
import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import com.orange.ai.reviewer_agent.domain.jira.IssueDetails;
import com.orange.ai.reviewer_agent.domain.jira.IssuesSummary;
import com.orange.ai.reviewer_agent.domain.jira.QualityGateStatus;
import com.orange.ai.reviewer_agent.domain.review.ReviewComment;
import com.orange.ai.reviewer_agent.domain.review.ReviewContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Application service that orchestrates the code review process
 * Uses Spring AI ChatClient with function calling capabilities
 */
@Service
public class ReviewOrchestrator {

    private final ChatClient chatClient;
    private final GitLabTools gitLabTools;
    private final JiraTools jiraTools;
    private final SonarQubeTools sonarQubeTools;
    private final AiCodeReviewService codeReviewService;

    public ReviewOrchestrator(
            ChatClient.Builder chatClientBuilder,
            GitLabTools gitLabTools,
            JiraTools jiraTools,
            SonarQubeTools sonarQubeTools,
            AiCodeReviewService codeReviewService) {
        this.chatClient = chatClientBuilder
                .defaultTools(gitLabTools, jiraTools, sonarQubeTools)
                .build();
        this.gitLabTools = gitLabTools;
        this.jiraTools = jiraTools;
        this.sonarQubeTools = sonarQubeTools;
        this.codeReviewService = codeReviewService;
    }

    /**
     * Initiates the merge request context gathering using AI function calling
     * The LLM will automatically call the fetchMergeRequestInfo tool
     */
    public MergeRequestInfo gatherMergeRequestContext(String mrUri) {
        String prompt = String.format("""
                I need you to fetch the merge request information for the following GitLab MR URI: %s
                
                Please retrieve all the details including:
                - Title and description
                - Source and target branches
                - Author information
                - Associated Jira ticket
                - Code diff
                
                Use the available tools to fetch this information.
                """, mrUri);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(MergeRequestInfo.class);
    }

    /**
     * Alternative direct approach - gets MR info without AI intermediation
     */
    public MergeRequestInfo gatherMergeRequestContextDirect(GitLabTools gitLabTools, String mrUri) {
        return gitLabTools.fetchMergeRequestInfo(mrUri);
    }

    /**
     * Orchestrates the complete code review workflow:
     * 1. Fetch MR information from GitLab
     * 2. Extract Jira ticket from MR title
     * 3. Fetch Jira issue details
     * 4. Fetch SonarQube quality gate status
     * 5. Fetch SonarQube issues for the branch
     * 6. Perform AI-powered code review
     * 7. Post review comment back to GitLab
     */
    public ReviewComment performCompleteReview(String mrUri, String sonarProjectKey) {
        // Step 1: Fetch MR information
        MergeRequestInfo mrInfo = gitLabTools.fetchMergeRequestInfo(mrUri);

        // Step 2 & 3: Get Jira details if ticket exists
        IssueDetails jiraDetails = null;
        if (mrInfo.jiraTicket() != null && !mrInfo.jiraTicket().isEmpty()) {
            try {
                jiraDetails = jiraTools.getIssueDetails(mrInfo.jiraTicket());
            } catch (Exception e) {
                // Log error but continue review without Jira context
                System.err.println("Failed to fetch Jira details: " + e.getMessage());
            }
        }

        // Step 4 & 5: Get SonarQube analysis
        QualityGateStatus qualityGate = null;
        IssuesSummary sonarIssues = null;
        try {
            qualityGate = sonarQubeTools.getQualityGateStatus(sonarProjectKey);
            sonarIssues = sonarQubeTools.getIssuesForBranch(sonarProjectKey, mrInfo.sourceBranch());
        } catch (Exception e) {
            // Log error but continue review without SonarQube context
            System.err.println("Failed to fetch SonarQube details: " + e.getMessage());
        }

        // Step 6: Build context and perform review
        ReviewContext context = new ReviewContext(mrInfo, jiraDetails, qualityGate, sonarIssues);
        ReviewComment review = codeReviewService.performReview(context);

        // Step 7: Post review comment back to GitLab
        String formattedComment = formatReviewAsMarkdown(review);
        gitLabTools.postMergeRequestComment(mrUri, formattedComment);

        return review;
    }

    /**
     * Formats the review comment as Markdown for GitLab
     */
    private String formatReviewAsMarkdown(ReviewComment review) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# 🤖 AI Code Review\n\n");

        // Summary
        markdown.append("## 📋 Summary\n");
        markdown.append(review.summary()).append("\n\n");

        // Issues
        if (review.issues() != null && !review.issues().isEmpty()) {
            markdown.append("## ⚠️ Issues Found\n\n");
            for (ReviewComment.Issue issue : review.issues()) {
                String emoji = switch (issue.severity().toUpperCase()) {
                    case "CRITICAL", "BLOCKER" -> "🔴";
                    case "MAJOR" -> "🟠";
                    case "MINOR" -> "🟡";
                    default -> "⚪";
                };

                markdown.append(String.format("%s **%s** - `%s`\n", emoji, issue.severity(), issue.category()));
                markdown.append(String.format("- **File:** `%s`", issue.filePath()));
                if (issue.lineNumber() != null) {
                    markdown.append(String.format(" (Line %d)", issue.lineNumber()));
                }
                markdown.append("\n");
                markdown.append(String.format("- **Description:** %s\n\n", issue.description()));
            }
        }

        // Suggestions
        if (review.suggestions() != null && !review.suggestions().isEmpty()) {
            markdown.append("## 💡 Suggestions\n\n");
            for (ReviewComment.Suggestion suggestion : review.suggestions()) {
                markdown.append(String.format("### `%s`", suggestion.filePath()));
                if (suggestion.lineNumber() != null) {
                    markdown.append(String.format(" (Line %d)", suggestion.lineNumber()));
                }
                markdown.append("\n");
                markdown.append(suggestion.description()).append("\n");

                if (suggestion.suggestedCode() != null && !suggestion.suggestedCode().isEmpty()) {
                    markdown.append("\n```java\n");
                    markdown.append(suggestion.suggestedCode()).append("\n");
                    markdown.append("```\n");
                }
                markdown.append("\n");
            }
        }

        // Overall Assessment
        markdown.append("## 🎯 Overall Assessment\n");
        markdown.append(review.overallAssessment()).append("\n\n");

        markdown.append("---\n");
        markdown.append("*Generated by AI Code Review Agent*\n");

        return markdown.toString();
    }
}
