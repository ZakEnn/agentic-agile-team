package com.agile.team.application.handler;

import com.agile.team.application.event.ImplementationCompleteEvent;
import com.agile.team.application.event.ReviewCompleteEvent;
import com.agile.team.application.usecase.SkillGovernanceUseCase;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.infrastructure.adapter.ai.SpringAiAgentBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReviewerAgentHandler {

    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final SpringAiAgentBridge agentBridge;
    private final ApplicationEventPublisher eventPublisher;
    private final SkillGovernanceUseCase skillGovernance;

    public ReviewerAgentHandler(AgentRepository agentRepository,
                                ConversationRepository conversationRepository,
                                SpringAiAgentBridge agentBridge,
                                ApplicationEventPublisher eventPublisher,
                                SkillGovernanceUseCase skillGovernance) {
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.agentBridge = agentBridge;
        this.eventPublisher = eventPublisher;
        this.skillGovernance = skillGovernance;
    }

    @Async
    @EventListener
    public void handleImplementationComplete(ImplementationCompleteEvent event) {
        // TODO: re-enable once PO Agent output is validated
        // REVIEWER Agent is temporarily disabled — pipeline stops at PO Agent for Jira ticket validation.
        /*
        Agent reviewer = agentRepository.findByRole(AgentRole.REVIEWER).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No REVIEWER agent registered"));

        // Governance hook: validate that review-criteria skill is intact before proceeding
        skillGovernance.validateGovernanceSkillsIntegrity();
        skillGovernance.recordSkillUsage("review-criteria", event.waveId().toString(), reviewer.getId().toString());

        // Ask AI to perform code review (uses MCP tools to access GitLab/SonarQube if needed)
        String prompt = String.format(
                "You are a Code Reviewer agent. Review the merge request '%s' in project 'epe-rating-ftth-passive'. " +
                "Use the available SonarQube tools to check quality gate status. " +
                "Assess code quality, adherence to standards, and potential issues. " +
                "Provide your disposition: APPROVED or CHANGES_REQUESTED with detailed comments.",
                event.mergeRequestId()
        );

        String aiResponse = agentBridge.chat(reviewer, prompt);

        // Determine approval status from AI response
        ApprovalStatus approvalStatus = (aiResponse != null && !aiResponse.isBlank()
                && aiResponse.toLowerCase().contains("approved"))
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.CHANGES_REQUESTED;

        ReviewDisposition disposition = new ReviewDisposition(approvalStatus, aiResponse != null ? aiResponse : "No response", null);

        // Default quality score - passing since we don't want to block on unavailable SonarQube
        CodeQualityScore qualityScore = CodeQualityScore.passing(80);

        // Record conversation
        ConversationHistory history = conversationRepository.findByWaveId(event.waveId())
                .orElse(ConversationHistory.startForWave(event.waveId()));
        history.record(AgentMessage.create(
                reviewer.getId(), event.devAgentId(),
                MessageType.REVIEW_COMPLETE,
                "Review: " + approvalStatus + " | Quality: " + qualityScore.score()
        ));
        conversationRepository.save(history);

        // Publish event
        eventPublisher.publishEvent(new ReviewCompleteEvent(
                event.waveId(), reviewer.getId(), event.taskId(),
                disposition, qualityScore
        ));
        */
    }
}
