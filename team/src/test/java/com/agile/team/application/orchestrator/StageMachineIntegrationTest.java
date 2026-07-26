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
    static class ScriptedDependencies {
        @Bean
        @Primary
        ScriptedLlmGateway scriptedLlmGateway() {
            return new ScriptedLlmGateway();
        }

        /**
         * Replaces the real command executor so the pipeline can be driven end to end
         * without invoking a build. {@code LocalCommandCodeExecutorTest} covers the
         * real thing against real processes.
         */
        @Bean
        @Primary
        com.agile.team.support.ScriptedCodeExecutor scriptedCodeExecutor() {
            return new com.agile.team.support.ScriptedCodeExecutor();
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

    @Autowired com.agile.team.support.ScriptedCodeExecutor codeExecutor;
    @Autowired com.agile.team.infrastructure.persistence.ArtifactCodec codec;

    @BeforeEach
    void setUp() {
        llm.reset();
        codeExecutor.reset();
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

    // --- the pipeline connected: SPEC -> approve -> BUILD -> VERIFY ---

    /*
     * DESIGN sits between SPEC and BUILD in the pipeline and lands in M5, so these
     * tests enqueue BUILD directly with the approved spec — exactly what the DESIGN
     * stage will hand over once it exists. Testing what M4 actually delivers rather
     * than stubbing a stage that has not been built.
     */

    @Test
    void shouldCarryAnApprovedSpecThroughBuildAndVerify() {
        llm.respondWith(DRAFT)
           .respondWith(CHANGE_PLAN)
           .respondWith(assessmentVerifyingAll());
        codeExecutor.succeedsWith("BUILD SUCCESS")   // developer build
                    .succeedsWith("Tests run: 4")     // developer tests
                    .succeedsWith("Tests run: 4");    // QA run

        givenApprovedSpecAndEnqueuedBuild();
        worker.drain(5);

        assertEquals(StageStatus.SUCCEEDED,
                stageRuns.findByWaveAndStage(waveId, SdlcStage.BUILD).orElseThrow().getStatus());
        assertEquals(StageStatus.SUCCEEDED,
                stageRuns.findByWaveAndStage(waveId, SdlcStage.VERIFY).orElseThrow().getStatus());
    }

    @Test
    void shouldFailTheBuildStageWhenTheBuildDoesNotCompile() {
        // The core guarantee of M4: an implementation that does not compile cannot
        // advance, whatever the model says about it.
        llm.respondWith(DRAFT).respondWith(CHANGE_PLAN);
        codeExecutor.failsWith(1, "SftpPoller.java:[12,5] cannot find symbol");

        givenApprovedSpecAndEnqueuedBuild();
        worker.pollOnce();

        StageRun build = stageRuns.findByWaveAndStage(waveId, SdlcStage.BUILD).orElseThrow();
        assertEquals(StageStatus.RETRYING, build.getStatus());
        assertTrue(build.getErrorMessage().contains("cannot find symbol"),
                "the compiler output must be retained as retry feedback");
        assertTrue(stageRuns.findByWaveAndStage(waveId, SdlcStage.VERIFY).isEmpty(),
                "a non-compiling change must not reach QA");
    }

    @Test
    void shouldFailVerifyWhenACriterionHasNoTestEvenThoughTheSuiteIsGreen() {
        llm.respondWith(DRAFT)
           .respondWith(CHANGE_PLAN)
           .respondWith(assessmentMissingOneCriterion());
        codeExecutor.succeedsWith("BUILD SUCCESS")
                    .succeedsWith("Tests run: 2")
                    .succeedsWith("Tests run: 2, Failures: 0");

        givenApprovedSpecAndEnqueuedBuild();
        worker.drain(5);

        StageRun verify = stageRuns.findByWaveAndStage(waveId, SdlcStage.VERIFY).orElseThrow();
        assertEquals(StageStatus.RETRYING, verify.getStatus());
        assertTrue(verify.getErrorMessage().contains("Unverified criteria"));
    }

    private void givenApprovedSpecAndEnqueuedBuild() {
        waveId = start();
        worker.pollOnce();
        approveSpec();
        // Stand in for the DESIGN handover.
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);
        stageRuns.save(StageRun.enqueue(waveId, SdlcStage.BUILD, codec.write(spec.toDraft()), 3));
    }

    // --- helpers ---

    private static final com.agile.team.domain.artifact.ChangePlan CHANGE_PLAN =
            new com.agile.team.domain.artifact.ChangePlan(
                    "Added a bounded retry loop.",
                    List.of(new com.agile.team.domain.artifact.ChangePlan.FileEdit(
                            "src/main/java/SftpPoller.java", "class SftpPoller {}")));

    private com.agile.team.application.agent.QaAgent.QaAssessment assessmentVerifyingAll() {
        return new com.agile.team.application.agent.QaAgent.QaAssessment(
                DRAFT.acceptanceCriteria().stream()
                        .map(c -> new com.agile.team.domain.artifact.QaVerdict.CriterionResult(
                                c, true, "verified by a test"))
                        .toList(),
                List.of(), "", "all verified");
    }

    private com.agile.team.application.agent.QaAgent.QaAssessment assessmentMissingOneCriterion() {
        List<String> criteria = DRAFT.acceptanceCriteria();
        return new com.agile.team.application.agent.QaAgent.QaAssessment(
                List.of(new com.agile.team.domain.artifact.QaVerdict.CriterionResult(
                                criteria.get(0), true, "verified by shouldRetryOnTimeout"),
                        new com.agile.team.domain.artifact.QaVerdict.CriterionResult(
                                criteria.get(1), false, "no test covers the alert path")),
                List.of(), "", "one criterion unverified");
    }

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
