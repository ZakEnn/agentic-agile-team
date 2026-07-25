package com.agile.team.application.orchestrator;

import com.agile.team.application.event.AgentTaskAssignedEvent;
import com.agile.team.application.event.QaCompleteEvent;
import com.agile.team.application.event.ReviewCompleteEvent;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class Orchestrator {

    private final WaveRepository waveRepository;
    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Orchestrator(WaveRepository waveRepository,
                        AgentRepository agentRepository,
                        ConversationRepository conversationRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.waveRepository = waveRepository;
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Starts a new wave (sprint). Creates the wave, initializes conversation history,
     * and assigns the first task to the PO agent.
     */
    public WaveId startWave(String waveName, String taskDescription, String keyword, String jiraTextLanguage) {
        Wave wave = new Wave(WaveId.generate(), waveName);
        waveRepository.save(wave);

        // Initialize conversation history for this wave
        ConversationHistory history = ConversationHistory.startForWave(wave.getId());
        conversationRepository.save(history);

        // Find the PO agent and assign the initial specification task
        Agent poAgent = agentRepository.findByRole(AgentRole.PO).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PO agent registered"));

        TaskId taskId = TaskId.generate();

        eventPublisher.publishEvent(new AgentTaskAssignedEvent(
                wave.getId(), poAgent.getId(), taskId, taskDescription, keyword, jiraTextLanguage
        ));

        return wave.getId();
    }

    /**
     * Handles review completion — if governance gate passed, transitions wave to review.
     */
    // TODO: re-enable once PO Agent output is validated
    // @Async
    // @EventListener
    public void handleReviewComplete(ReviewCompleteEvent event) {
        /*
        Wave wave = waveRepository.findById(event.waveId())
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + event.waveId()));

        if (wave.getStatus() == com.agile.team.domain.wave.WaveStatus.IN_PROGRESS) {
            wave.moveToReview("REVIEWER_AGENT:" + event.reviewerAgentId().value());
            waveRepository.save(wave);
        }
        */
    }

    /**
     * Handles QA completion — if passed, completes the wave.
     */
    // TODO: re-enable once PO Agent output is validated
    // @Async
    // @EventListener
    public void handleQaComplete(QaCompleteEvent event) {
        /*
        Wave wave = waveRepository.findById(event.waveId())
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + event.waveId()));

        if (event.passed()) {
            // Wave completed successfully - but we need review data to pass governance gate
            // The complete() method on Wave enforces the gate, so we only mark success
            // if the wave is already in REVIEW state (set by handleReviewComplete)
            if (wave.getStatus() == com.agile.team.domain.wave.WaveStatus.IN_REVIEW) {
                // Note: actual completion with governance gate is handled via use case
                waveRepository.save(wave);
            }
        } else {
            wave.fail("QA_AGENT");
            waveRepository.save(wave);
        }
        */
    }
}
