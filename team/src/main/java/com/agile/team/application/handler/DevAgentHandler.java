package com.agile.team.application.handler;

import com.agile.team.application.event.ImplementationCompleteEvent;
import com.agile.team.application.event.SpecificationReadyEvent;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.task.Task;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.adapter.ai.SpringAiAgentBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DevAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(DevAgentHandler.class);

    private final WaveRepository waveRepository;
    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final SpringAiAgentBridge agentBridge;
    private final ApplicationEventPublisher eventPublisher;

    public DevAgentHandler(WaveRepository waveRepository,
                           AgentRepository agentRepository,
                           ConversationRepository conversationRepository,
                           SpringAiAgentBridge agentBridge,
                           ApplicationEventPublisher eventPublisher) {
        this.waveRepository = waveRepository;
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.agentBridge = agentBridge;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleSpecificationReady(SpecificationReadyEvent event) {
        // TODO: re-enable once PO Agent output is validated
        // DEV Agent is temporarily disabled — pipeline stops at PO Agent for Jira ticket validation.
        /*
        Agent devAgent = agentRepository.findByRole(AgentRole.DEV).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No DEV agent registered"));

        Wave wave = waveRepository.findById(event.waveId())
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + event.waveId()));

        Specification spec = wave.getSpecifications().stream()
                .filter(s -> s.getId().equals(event.specificationId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Specification not found"));

        // Ask AI to plan the implementation using GitLab tools
        String branchName = "feature/" + spec.getId().value().toString().substring(0, 8);
        String prompt = String.format(
                "You are a Developer agent. Based on this specification, plan the implementation.\n" +
                "Title: %s\nContent: %s\nAcceptance Criteria: %s\n\n" +
                "Using the available GitLab tools, create a branch named '%s' from 'main' " +
                "in the project 'epe-rating-ftth-passive' (search for it if needed), " +
                "then create a merge request with title '%s'. " +
                "After completing the operations, respond with a summary of what you did including the MR URL.",
                spec.getTitle(), spec.getContent(), spec.getAcceptanceCriteria(),
                branchName, spec.getTitle()
        );

        String aiResponse = agentBridge.chatWithTools(devAgent, prompt);

        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("DEV Agent: AI returned empty response, proceeding with branch name as MR ref");
        }

        // Extract MR ID from AI response or use branch name as fallback
        String mrId = branchName;

        // Create task and track progress
        TaskId taskId = TaskId.generate();
        Task task = new Task(taskId, spec.getTitle(), spec.getContent());
        task.startProgress();
        task.submitForReview();
        wave.addTask(task);
        waveRepository.save(wave);

        // Record conversation
        ConversationHistory history = conversationRepository.findByWaveId(event.waveId())
                .orElse(ConversationHistory.startForWave(event.waveId()));
        history.record(AgentMessage.create(
                devAgent.getId(), devAgent.getId(),
                MessageType.IMPLEMENTATION_COMPLETE,
                "Implementation complete. MR: " + mrId
        ));
        conversationRepository.save(history);

        // Publish event
        eventPublisher.publishEvent(new ImplementationCompleteEvent(
                event.waveId(), devAgent.getId(), taskId, mrId
        ));
        */
        log.info("[DEV Agent] Handler disabled — PO Agent validation phase. Event ignored: {}", event);
    }
}
