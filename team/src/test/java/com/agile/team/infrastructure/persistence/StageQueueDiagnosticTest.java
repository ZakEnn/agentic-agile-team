package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.stage.StageRunRepository;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.persistence.repository.StageRunJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the queue's SQL against the real database rather than assuming it works. */
@SpringBootTest
@ActiveProfiles("test")
class StageQueueDiagnosticTest {

    @Autowired StageRunJpaRepository jpa;
    @Autowired StageRunRepository stageRuns;
    @Autowired WaveRepository waves;
    @Autowired com.agile.team.infrastructure.persistence.repository.ConversationJpaRepository conversationJpa;
    @Autowired com.agile.team.infrastructure.persistence.repository.WaveJpaRepository waveJpa;

    /**
     * Spring caches one application context across test classes, so the H2 database
     * is shared. Leftover PENDING rows from another class make queue assertions
     * order-dependent — which showed up as a genuinely flaky claim test.
     */
    @org.junit.jupiter.api.BeforeEach
    void resetQueue() {
        jpa.deleteAll();
        conversationJpa.deleteAll();
        waveJpa.deleteAll();
    }

    @Test
    void claimableSelectMustFindAPendingRow() {
        WaveId waveId = givenWave();
        stageRuns.save(StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3));

        assertEquals(1, jpa.findByWaveIdOrderByCreatedAtAsc(waveId.value()).size(),
                "row must exist");

        List<?> withoutLock = jpa.selectClaimableWithoutLock(Instant.now().plusSeconds(60), 10);
        assertFalse(withoutLock.isEmpty(), "plain claimable select must find the pending row");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void claimMustEstablishOwnershipExactlyOnce() {
        WaveId waveId = givenWave();
        StageRun run = StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3);
        stageRuns.save(run);

        int first = jpa.claim(run.getId(), "instance-a", Instant.now());
        int second = jpa.claim(run.getId(), "instance-b", Instant.now());

        // The conditional UPDATE is the correctness guarantee: the status='PENDING'
        // predicate fails for whoever arrives second, regardless of locking hints.
        assertEquals(1, first, "the first claimant must win");
        assertEquals(0, second, "the second claimant must find nothing to claim");
    }

    @Test
    void adapterClaimMustReturnTheClaimedRun() {
        WaveId waveId = givenWave();
        stageRuns.save(StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3));

        List<StageRun> claimed = stageRuns.claimDueStages("instance-a", 10);

        // Scoped to this wave rather than asserting on the whole queue, so the test
        // does not depend on what else happens to be enqueued.
        StageRun mine = claimed.stream()
                .filter(r -> r.getWaveId().equals(waveId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "adapter must claim this wave's due stage; claimed " + claimed.size() + " other(s)"));
        assertEquals("instance-a", mine.getClaimedBy());
    }

    private WaveId givenWave() {
        Wave wave = new Wave(WaveId.generate(), "diag " + System.nanoTime(),
                new WaveContext("EPE", null, null, "English"));
        waves.save(wave);
        return wave.getId();
    }
}
