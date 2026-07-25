package com.agile.team.application.handler;

import com.agile.team.application.event.QaCompleteEvent;
import com.agile.team.application.event.ReviewCompleteEvent;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.review.ReviewGate;
import com.agile.team.infrastructure.adapter.ai.SpringAiAgentBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class QaAgentHandler {

    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final SpringAiAgentBridge agentBridge;
    private final ApplicationEventPublisher eventPublisher;

    public QaAgentHandler(AgentRepository agentRepository,
                          ConversationRepository conversationRepository,
                          SpringAiAgentBridge agentBridge,
                          ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.agentBridge = agentBridge;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleReviewComplete(ReviewCompleteEvent event) {
        // TODO: re-enable once PO Agent output is validated
        // QA Agent is temporarily disabled — pipeline stops at PO Agent for Jira ticket validation.
        /*
        // QA only runs if governance gate passes
        if (!ReviewGate.canComplete(event.reviewDisposition(), event.qualityScore())) {
            // Record failure and stop pipeline
            ConversationHistory history = conversationRepository.findByWaveId(event.waveId())
                    .orElse(ConversationHistory.startForWave(event.waveId()));

            Agent qaAgent = agentRepository.findByRole(AgentRole.QA).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No QA agent registered"));

            history.record(AgentMessage.create(
                    qaAgent.getId(), event.reviewerAgentId(),
                    MessageType.GOVERNANCE_CHECK,
                    "QA blocked: ReviewGate check failed. Review: " + event.reviewDisposition().status() +
                    ", Quality: " + event.qualityScore().passed()
            ));
            conversationRepository.save(history);

            eventPublisher.publishEvent(new QaCompleteEvent(
                    event.waveId(), event.taskId(), false,
                    "Blocked by governance gate"
            ));
            return;
        }

        Agent qaAgent = agentRepository.findByRole(AgentRole.QA).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No QA agent registered"));

        // Ask AI to perform QA validation
        String prompt = String.format(
                "You are a QA agent. The code review has passed with disposition: %s " +
                "and quality score: %.1f. Perform final validation and acceptance testing. " +
                "Report whether the implementation meets the acceptance criteria.",
                event.reviewDisposition().status(), event.qualityScore().score()
        );

        String aiResponse = agentBridge.chat(qaAgent, prompt);
        boolean passed = aiResponse.toLowerCase().contains("passed") ||
                         aiResponse.toLowerCase().contains("accepted");

        // Record conversation
        ConversationHistory history = conversationRepository.findByWaveId(event.waveId())
                .orElse(ConversationHistory.startForWave(event.waveId()));
        history.record(AgentMessage.create(
                qaAgent.getId(), event.reviewerAgentId(),
                MessageType.QA_COMPLETE,
                "QA " + (passed ? "PASSED" : "FAILED") + ": " + aiResponse
        ));
        conversationRepository.save(history);

        eventPublisher.publishEvent(new QaCompleteEvent(
                event.waveId(), event.taskId(), passed, aiResponse
        ));
        */
    }
}
