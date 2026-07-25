package com.orange.ai.reviewer_agent.web;

import com.orange.ai.reviewer_agent.application.review.ReviewOrchestrator;
import com.orange.ai.reviewer_agent.application.tools.GitLabTools;
import com.orange.ai.reviewer_agent.domain.gitlab.MergeRequestInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitlabAgentController {

    private final ReviewOrchestrator reviewOrchestrator;
    private final GitLabTools gitLabTools;

    public GitlabAgentController(ReviewOrchestrator reviewOrchestrator, GitLabTools gitLabTools) {
        this.reviewOrchestrator = reviewOrchestrator;
        this.gitLabTools = gitLabTools;
    }

    /**
     * Endpoint using AI-powered function calling to fetch MR info
     */
    @GetMapping(path = "/api/mr/info/ai", produces = "application/json")
    public MergeRequestInfo getMergeRequestInfoWithAI(@RequestParam String mrUri) {
        return reviewOrchestrator.gatherMergeRequestContext(mrUri);
    }

    /**
     * Endpoint using direct tool invocation (no AI intermediation)
     */
    @GetMapping(path = "/api/mr/info", produces = "application/json")
    public MergeRequestInfo getMergeRequestInfo(@RequestParam String mrUri) {
        return reviewOrchestrator.gatherMergeRequestContextDirect(gitLabTools, mrUri);
    }
}
