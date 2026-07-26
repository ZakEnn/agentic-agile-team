package com.agile.team.application.orchestrator;

import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationStatus;
import com.agile.team.domain.stage.*;
import com.agile.team.domain.wave.*;
import com.agile.team.support.ScriptedLlmGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The durable stage machine, end to end, against a real database and the real queue.
 * <p>
 * Drives {@link StageWorker#pollOnce()} directly rather than waiting on the
 * scheduler, so the test exercises the production code path with no sleeping and no
 * flakiness. The LLM is the only thing replaced.
 */
@SpringBootTest
@ActiveProfiles("test")
class StageMachineIntegrationTest {

    @TestConfiguration
    static class ScriptedLlm {
        @Bean
        @Primary
        ScriptedLlmGateway scriptedLlmGateway() {
            return new ScriptedLlmGateway();
        }
    }

    private static final SpecDraft DRAFT = new SpecDraft(
            "Add retry to the SFTP poller",
            "Retry transient failures with exponential backoff.",
            List.of("Given a socket timeout, when polling, then it retries up to 3 times",
                    "Given retries are exhausted, then an alert is raised"),
            List.of("Changing the polling schedule"));

    @Autowired Orchestrator orchestrator;
    @Autowired StageWorker worker;
    @Autowired WaveRepository waves;
    @Autowired StageRunRepository stageRuns;
    @Autowired ConversationRepository conversations;
    @Autowired ScriptedLlmGateway llm;

    // Spring caches the context across tests, so the H2 database is shared. Without
    // an explicit reset, one test's leftover PENDING stages are claimable by the
    // next — which showed up as a worker claiming two stages instead of one.
    @Autowired com.agile.team.infrastructure.persistence.repository.StageRunJpaRepository stageRunJpa;
    @Autowired com.agile.team.infrastructure.persistence.repository.ConversationJpaRepository conversationJpa;
    @Autowired com.agile.team.infrastructure.persistence.repository.WaveJpaRepository waveJpa;

    private WaveId waveId;

    @BeforeEach
    void setUp() {
        llm.reset();
        // Order matters: stage_run and conversations reference waves.
        stageRunJpa.deleteAll();
        conversationJpa.deleteAll();
        waveJpa.deleteAll();
    }

    @Test
    void shouldEnqueueTheSpecStageWithoutRunningIt() {
        // startWave returns immediately: the caller no longer blocks on a model call.
        waveId = start();

        StageRun run = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        assertEquals(StageStatus.PENDING, run.getStatus());
        assertEquals(0, run.getAttempt());
        assertEquals(0, llm.callCount(), "no agent should have run yet");
    }

    @Test
    void shouldRunTheSpecStageWhenTheWorkerPolls() {
        llm.respondWith(DRAFT);
        waveId = start();

        int executed = worker.pollOnce();

        assertEquals(1, executed);
        StageRun run = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        assertEquals(StageStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getAttempt());

        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);
        assertEquals(2, spec.getAcceptanceCriteria().size());
    }

    @Test
    void shouldParkAtTheSpecApprovalGateAndNotAdvance() {
        llm.respondWith(DRAFT);
        waveId = start();

        worker.pollOnce();
        // Draining further must not move the wave: the gate is REQUIRED and undecided.
        worker.drain(3);

        assertEquals(WaveStatus.PLANNING, waves.findById(waveId).orElseThrow().getStatus());
        assertEquals(SpecificationStatus.DRAFT,
                waves.findById(waveId).orElseThrow().getSpecifications().get(0).getStatus());
        assertTrue(stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).isEmpty());
        assertTrue(conversationPayloads().stream()
                .anyMatch(p -> p.contains("awaiting decision at gate spec-approval")));
    }

    @Test
    void shouldNeverReportSuccessForAStageWithNoHandler() {
        // DESIGN has no handler until M5. The machine must stop honestly rather than
        // let a wave "complete" without ever being designed, built or tested.
        llm.respondWith(DRAFT);
        waveId = start();
        worker.pollOnce();
        approveSpec();

        worker.drain(3);

        StageRun design = stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).orElseThrow();
        assertNotEquals(StageStatus.SUCCEEDED, design.getStatus());
        assertTrue(conversationPayloads().stream()
                .anyMatch(p -> p.contains("No handler registered for stage DESIGN")));
    }

    @Test
    void shouldRecordTokensAgainstTheWave() {
        llm.respondWith(DRAFT).withUsagePerCall(LlmGateway.TokenUsage.of(400, 200));
        waveId = start();

        worker.pollOnce();

        assertEquals(600, waves.findById(waveId).orElseThrow().getTokensUsed());
    }

    @Test
    void shouldRetryAFailedStageAndSucceedOnTheSecondAttempt() {
        llm.failWith(new LlmGateway.LlmGatewayException("model timed out"));
        llm.respondWith(DRAFT);
        waveId = start();

        worker.pollOnce();
        StageRun afterFailure = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        assertEquals(StageStatus.RETRYING, afterFailure.getStatus());
        assertEquals(1, afterFailure.getAttempt());

        // Simulate the backoff elapsing rather than waiting for it.
        makeAvailableNow(afterFailure);
        worker.pollOnce();

        StageRun afterRetry = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        assertEquals(StageStatus.SUCCEEDED, afterRetry.getStatus());
        assertEquals(2, afterRetry.getAttempt());
    }

    @Test
    void shouldDeadLetterAfterExhaustingAttemptsAndFailTheWave() {
        for (int i = 0; i < 3; i++) {
            llm.failWith(new LlmGateway.LlmGatewayException("persistent failure"));
        }
        waveId = start();

        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int executed = worker.pollOnce();
            StageRun current = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
            trace.append("\n  poll %d: executed=%d attempt=%d/%d status=%s".formatted(
                    i, executed, current.getAttempt(), current.getMaxAttempts(), current.getStatus()));
            makeAvailableNow(current);
        }

        StageRun run = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        assertEquals(StageStatus.DEAD_LETTER, run.getStatus(), "trace:" + trace);
        assertEquals(WaveStatus.FAILED, waves.findById(waveId).orElseThrow().getStatus());
    }

    @Test
    void shouldResumeAfterAnInstanceDiesMidStage() {
        // The property that the in-JVM event bus could not provide: work claimed by an
        // instance that disappears is recovered rather than lost.
        llm.respondWith(DRAFT);
        waveId = start();

        StageRun run = stageRuns.findByWaveAndStage(waveId, SdlcStage.SPEC).orElseThrow();
        run.claim("instance-that-will-die");
        stageRuns.save(run);
        assertEquals(StageStatus.RUNNING, stageRuns.findById(run.getId()).orElseThrow().getStatus());

        // Nothing is claimable while the (now dead) instance holds it.
        assertEquals(0, worker.pollOnce());

        // Once the claim is stale, another instance recovers it.
        stageRuns.reclaimStaleRunning(0);
        int executed = worker.pollOnce();

        assertEquals(1, executed);
        assertEquals(StageStatus.SUCCEEDED,
                stageRuns.findById(run.getId()).orElseThrow().getStatus());
    }

    @Test
    void shouldNotDoubleClaimAStage() {
        llm.respondWith(DRAFT);
        waveId = start();

        // Two claim rounds; the second must find nothing, because the first took it.
        StageRun claimedFirst = stageRuns.claimDueStages("instance-a", 10).get(0);
        List<StageRun> second = stageRuns.claimDueStages("instance-b", 10);

        assertEquals("instance-a", claimedFirst.getClaimedBy());
        assertTrue(second.isEmpty(), "a claimed stage must not be handed to a second instance");
    }

    @Test
    void shouldStopTheWaveWhenTheTokenBudgetIsExceeded() {
        llm.respondWith(DRAFT).withUsagePerCall(LlmGateway.TokenUsage.of(400_000, 200_000));
        waveId = start();
        worker.pollOnce();

        // First stage alone consumes 600k against a 500k cap; the next stage must not run.
        Wave wave = waves.findById(waveId).orElseThrow();
        assertTrue(wave.getTokensUsed() > 500_000);

        approveSpec();
        worker.drain(3);

        StageRun design = stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).orElseThrow();
        assertEquals(StageStatus.BUDGET_EXCEEDED, design.getStatus());
        assertTrue(conversationPayloads().stream().anyMatch(p -> p.contains("exceeding its budget")));
    }

    @Test
    void shouldEnqueueTheNextStageOnlyAfterHumanApproval() {
        llm.respondWith(DRAFT);
        waveId = start();
        worker.pollOnce();

        assertTrue(stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).isEmpty(),
                "nothing downstream may be enqueued before the gate is decided");

        approveSpec();

        assertTrue(stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).isPresent());
        assertEquals(WaveStatus.IN_PROGRESS, waves.findById(waveId).orElseThrow().getStatus());
    }

    @Test
    void shouldFailTheWaveWhenTheSpecIsRejected() {
        llm.respondWith(DRAFT);
        waveId = start();
        worker.pollOnce();

        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);
        orchestrator.decideSpecification(waveId, spec.getId(),
                GateDecision.rejectedBy(GateName.SPEC_APPROVAL, "alice", "criteria are not testable"));

        Wave wave = waves.findById(waveId).orElseThrow();
        assertEquals(WaveStatus.FAILED, wave.getStatus());
        assertEquals(SpecificationStatus.REJECTED, wave.getSpecifications().get(0).getStatus());
        assertTrue(stageRuns.findByWaveAndStage(waveId, SdlcStage.DESIGN).isEmpty());
    }

    @Test
    void shouldWriteAReadableAuditTrail() {
        llm.respondWith(DRAFT);
        waveId = start();
        worker.pollOnce();

        List<MessageType> types = conversations.findByWaveId(waveId).orElseThrow()
                .getMessages().stream().map(m -> m.type()).toList();

        assertTrue(types.contains(MessageType.SPECIFICATION_REQUEST));
        assertTrue(types.contains(MessageType.STAGE_STARTED));
        assertTrue(types.contains(MessageType.STAGE_COMPLETED));
    }

    // --- helpers ---

    private WaveId start() {
        return orchestrator.startWave(new Orchestrator.StartWaveCommand(
                "Sprint " + System.nanoTime(),
                "Make the SFTP poller resilient to transient failures",
                null,
                new WaveContext("EPE", "epe/app", "SCA", "English")));
    }

    private void approveSpec() {
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);
        orchestrator.decideSpecification(waveId, spec.getId(),
                GateDecision.approvedBy(GateName.SPEC_APPROVAL, "alice", "looks right"));
    }

    /** Bring a retrying stage forward instead of sleeping through its backoff. */
    private void makeAvailableNow(StageRun run) {
        run.makeAvailable();
        stageRuns.save(run);
    }

    private List<String> conversationPayloads() {
        return conversations.findByWaveId(waveId).orElseThrow()
                .getMessages().stream().map(m -> m.payload()).toList();
    }
}
