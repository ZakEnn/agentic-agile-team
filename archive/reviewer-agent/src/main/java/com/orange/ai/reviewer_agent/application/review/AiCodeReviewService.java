package com.orange.ai.reviewer_agent.application.review;

import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import com.orange.ai.reviewer_agent.domain.jira.IssueDetails;
import com.orange.ai.reviewer_agent.domain.jira.IssuesSummary;
import com.orange.ai.reviewer_agent.domain.jira.QualityGateStatus;
import com.orange.ai.reviewer_agent.domain.review.CodeReviewService;
import com.orange.ai.reviewer_agent.domain.review.ReviewComment;
import com.orange.ai.reviewer_agent.domain.review.ReviewContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementation of CodeReviewService using Spring AI
 */
@Service
public class AiCodeReviewService implements CodeReviewService {

    private final ChatClient chatClient;

    private static final String REVIEW_PROMPT_TEMPLATE = """
            You are an expert code reviewer. Perform a thorough code review of the following merge request.
            
            ## Merge Request Information
            Title: {title}
            Description: {description}
            Source Branch: {sourceBranch}
            Target Branch: {targetBranch}
            Author: {author}
            
            ## Jira Ticket Context
            Ticket: {jiraKey}
            Summary: {jiraSummary}
            Description: {jiraDescription}
            Acceptance Criteria:
            {acceptanceCriteria}
            
            ## SonarQube Analysis
            Quality Gate Status: {qualityGateStatus}
            Total Issues: {sonarIssuesCount}
            Critical Issues: {criticalIssues}
            
            ## Code Diff
            ```
            {diff}
            ```
            
            ## Your Task
            Analyze the code changes and provide a structured review that includes:
            
            1. **Summary**: A brief overview of the changes and their quality
            2. **Issues**: List any problems found (security, bugs, code smells, performance)
               - For each issue, specify: severity, file path, line number, description, and category
            3. **Suggestions**: Constructive recommendations for improvement
               - For each suggestion, specify: file path, line number, description, and optionally suggested code
            4. **Overall Assessment**:
               - Does the code meet the Jira acceptance criteria?
               - Are there any blocking issues?
               - Is the code ready to merge?
            
            Focus on:
            - Code quality and maintainability
            - Adherence to best practices
            - Security vulnerabilities
            - Performance concerns
            - Alignment with Jira requirements
            - SonarQube findings
            
            Be constructive, specific, and actionable in your feedback.
            """;

    public AiCodeReviewService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ReviewComment performReview(MergeRequestInfo mergeRequestInfo) {
        throw new UnsupportedOperationException(
                "Use performReview(ReviewContext) for complete review with all context");
    }

    /**
     * Performs a comprehensive code review with full context
     */
    public ReviewComment performReview(ReviewContext context) {
        String prompt = buildPrompt(context);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ReviewComment.class);
    }

    private String buildPrompt(ReviewContext context) {
        MergeRequestInfo mr = context.mergeRequest();
        IssueDetails jira = context.jiraIssue();
        QualityGateStatus qg = context.qualityGate();
        IssuesSummary sonar = context.issuesSummary();

        String acceptanceCriteria = jira != null && jira.acceptanceCriteria() != null
                ? String.join("\n", jira.acceptanceCriteria())
                : "No acceptance criteria specified";

        long criticalIssues = sonar != null && sonar.issues() != null
                ? sonar.issues().stream()
                    .filter(i -> "CRITICAL".equals(i.severity()) || "BLOCKER".equals(i.severity()))
                    .count()
                : 0;

        Map<String, Object> promptVars = new java.util.HashMap<>();
        promptVars.put("title", mr.title() != null ? mr.title() : "N/A");
        promptVars.put("description", mr.description() != null ? mr.description() : "N/A");
        promptVars.put("sourceBranch", mr.sourceBranch() != null ? mr.sourceBranch() : "N/A");
        promptVars.put("targetBranch", mr.targetBranch() != null ? mr.targetBranch() : "N/A");
        promptVars.put("author", mr.authorName() != null ? mr.authorName() : "N/A");
        promptVars.put("jiraKey", jira != null ? jira.key() : "N/A");
        promptVars.put("jiraSummary", jira != null ? jira.summary() : "N/A");
        promptVars.put("jiraDescription", jira != null && jira.description() != null ? jira.description() : "N/A");
        promptVars.put("acceptanceCriteria", acceptanceCriteria);
        promptVars.put("qualityGateStatus", qg != null ? qg.status() : "N/A");
        promptVars.put("sonarIssuesCount", sonar != null ? sonar.totalIssues() : 0);
        promptVars.put("criticalIssues", criticalIssues);
        promptVars.put("diff", mr.diff() != null && !mr.diff().isEmpty() ? mr.diff() : "No diff available");

        PromptTemplate promptTemplate = new PromptTemplate(REVIEW_PROMPT_TEMPLATE);
        return promptTemplate.render(promptVars);
    }
}
