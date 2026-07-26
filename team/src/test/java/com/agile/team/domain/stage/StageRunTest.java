package com.agile.team.domain.stage;

import com.agile.team.domain.wave.WaveId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retry, backoff and dead-lettering — the behaviour that turns "an agent failed"
 * from an unbounded loop into a bounded, inspectable outcome.
 */
class StageRunTest {

    private static final WaveId WAVE = WaveId.generate();

    @Test
    void shouldEnqueueAsPendingAndImmediatelyDue() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 3);

        assertEquals(StageStatus.PENDING, run.getStatus());
        assertEquals(0, run.getAttempt());
        assertTrue(run.getAvailableAt().isBefore(Instant.now().plusSeconds(1)));
        assertTrue(run.getStatus().isClaimable());
    }

    @Test
    void shouldDeriveIdempotencyKeyFromWaveAndStage() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.REVIEW, null, 3);
        assertEquals(WAVE.value() + ":REVIEW", run.getIdempotencyKey());
        // Same wave and stage always produce the same key, which is what makes a
        // duplicate enqueue a constraint violation rather than duplicated work.
        assertEquals(run.getIdempotencyKey(),
                StageRun.idempotencyKeyFor(WAVE, SdlcStage.REVIEW));
    }

    @Test
    void shouldIncrementAttemptOnClaim() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 3);
        run.claim("instance-a");

        assertEquals(StageStatus.RUNNING, run.getStatus());
        assertEquals(1, run.getAttempt());
        assertEquals("instance-a", run.getClaimedBy());
        assertNotNull(run.getClaimedAt());
    }

    @Test
    void shouldRefuseToClaimAStageThatIsNotPending() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 3);
        run.claim("instance-a");

        assertThrows(IllegalStateException.class, () -> run.claim("instance-b"));
    }

    @Test
    void shouldRetryWithBackoffWhileAttemptsRemain() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 3);
        run.claim("i");
        run.fail("model timed out", 120);

        assertEquals(StageStatus.RETRYING, run.getStatus());
        assertTrue(run.getAvailableAt().isAfter(Instant.now()),
                "a retry must wait for its backoff rather than spin");
        assertEquals(120, run.getTokensUsed(),
                "tokens spent on a failed attempt still count against the budget");
        assertNull(run.getClaimedBy(), "a failed claim must be released");
    }

    @Test
    void shouldDeadLetterWhenAttemptsAreExhausted() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 2);

        run.claim("i");
        run.fail("first failure", 10);
        run.makeAvailable();
        run.claim("i");
        run.fail("second failure", 10);

        assertEquals(StageStatus.DEAD_LETTER, run.getStatus());
        assertTrue(run.getStatus().isTerminal());
        assertTrue(run.getStatus().isFailure());
        assertEquals(20, run.getTokensUsed());
        assertFalse(run.hasAttemptsRemaining());
    }

    @Test
    void shouldGrowBackoffExponentiallyAndCapIt() {
        assertEquals(Duration.ofSeconds(10), StageRun.backoffFor(1));
        assertEquals(Duration.ofSeconds(20), StageRun.backoffFor(2));
        assertEquals(Duration.ofSeconds(40), StageRun.backoffFor(3));
        // Capped so a long-lived stuck stage does not drift into never retrying.
        assertEquals(Duration.ofMinutes(10), StageRun.backoffFor(20));
    }

    @Test
    void shouldAccumulateTokensAcrossAttempts() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 5);
        run.claim("i");
        run.fail("boom", 100);
        run.makeAvailable();
        run.claim("i");
        run.succeed("{}", 250);

        assertEquals(StageStatus.SUCCEEDED, run.getStatus());
        assertEquals(350, run.getTokensUsed());
        assertNull(run.getErrorMessage(), "success must clear the previous error");
    }

    @Test
    void shouldReclaimAnAbandonedRunningStageAsAFailedAttempt() {
        // An instance that dies mid-stage leaves a RUNNING row. Treating recovery as
        // a failed attempt means a genuinely stuck stage still dead-letters rather
        // than being retried forever by successive instances.
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 2);
        run.claim("dead-instance");

        run.reclaimAsFailed("instance disappeared");

        assertEquals(StageStatus.RETRYING, run.getStatus());
        assertEquals(1, run.getAttempt());
    }

    @Test
    void shouldIgnoreReclaimForAStageThatIsNotRunning() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 2);
        run.reclaimAsFailed("spurious");
        assertEquals(StageStatus.PENDING, run.getStatus());
    }

    @Test
    void shouldParkAtAGateWithoutConsumingAnAttempt() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.SPEC, "{}", 3);
        run.claim("i");
        run.awaitApproval();

        assertEquals(StageStatus.AWAITING_APPROVAL, run.getStatus());
        assertFalse(run.getStatus().isClaimable());
        assertFalse(run.getStatus().isTerminal(), "a parked stage is waiting, not finished");
        assertNull(run.getClaimedBy());
    }

    @Test
    void shouldMarkBudgetExceededAsTerminalFailure() {
        StageRun run = StageRun.enqueue(WAVE, SdlcStage.BUILD, "{}", 3);
        run.exceedBudget("wave over budget");

        assertEquals(StageStatus.BUDGET_EXCEEDED, run.getStatus());
        assertTrue(run.getStatus().isTerminal());
        assertTrue(run.getStatus().isFailure());
    }

    @Test
    void shouldOrderStagesAsThePipeline() {
        assertEquals(SdlcStage.DESIGN, SdlcStage.SPEC.next());
        assertEquals(SdlcStage.BUILD, SdlcStage.DESIGN.next());
        assertEquals(SdlcStage.VERIFY, SdlcStage.BUILD.next());
        assertEquals(SdlcStage.REVIEW, SdlcStage.VERIFY.next());
        assertEquals(SdlcStage.RELEASE, SdlcStage.REVIEW.next());
        assertNull(SdlcStage.RELEASE.next());
    }

    @Test
    void shouldDeclareGatesOnTheRightStages() {
        assertTrue(SdlcStage.SPEC.hasGate());
        assertTrue(SdlcStage.REVIEW.hasGate());
        assertTrue(SdlcStage.RELEASE.hasGate());
        // Design, build and verify run unattended — that is where the time savings are.
        assertFalse(SdlcStage.DESIGN.hasGate());
        assertFalse(SdlcStage.BUILD.hasGate());
        assertFalse(SdlcStage.VERIFY.hasGate());
    }
}
