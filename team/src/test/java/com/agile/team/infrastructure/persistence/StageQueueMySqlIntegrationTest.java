package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.stage.StageRunRepository;
import com.agile.team.domain.stage.StageStatus;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.persistence.repository.StageRunJpaRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 3 (DECISIONS.md D-005): the parts only real MySQL can verify.
 * <p>
 * Two things are genuinely MySQL-specific and untestable on H2:
 * <ol>
 *   <li>the Flyway migration chain, which uses {@code ENGINE=InnoDB} and MySQL types;</li>
 *   <li>{@code FOR UPDATE SKIP LOCKED}, the mechanism that lets several Cloud Foundry
 *       instances claim from one queue without blocking each other.</li>
 * </ol>
 * Excluded from the default build because it needs Docker. Run it with
 * {@code mvn verify -Dgroups=integration -DexcludedTestGroups=}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("mysql-it")
class StageQueueMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("agile_team");

    @Autowired StageRunRepository stageRuns;
    @Autowired StageRunJpaRepository jpa;
    @Autowired WaveRepository waves;

    @Test
    void flywayMigrationChainAppliesOnRealMySql() {
        // The context started at all, which means V1.0.0 through V1.6.0 applied
        // against real MySQL rather than H2's approximation of it.
        assertNotNull(jpa);
        assertTrue(jpa.count() >= 0);
    }

    @Test
    void skipLockedClaimIsUsedWithoutFallingBack() {
        WaveId waveId = givenWave();
        stageRuns.save(StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3));

        // Calls the native SKIP LOCKED query directly. If MySQL rejected the syntax
        // this would throw rather than silently degrade to the fallback.
        List<?> claimable = jpa.selectClaimable(Instant.now(), 10);

        assertFalse(claimable.isEmpty());
    }

    @Test
    void concurrentInstancesClaimDisjointSetsOfStages() throws Exception {
        // The property the whole design rests on: several instances polling one table
        // must never both execute the same stage.
        for (int i = 0; i < 24; i++) {
            stageRuns.save(StageRun.enqueue(givenWave(), SdlcStage.SPEC, "{}", 3));
        }

        int instances = 4;
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        try {
            List<Future<List<StageRun>>> futures = IntStream.range(0, instances)
                    .mapToObj(i -> pool.submit(() -> stageRuns.claimDueStages("instance-" + i, 10)))
                    .toList();

            List<StageRun> all = new ArrayList<>();
            for (Future<List<StageRun>> future : futures) {
                all.addAll(future.get(60, TimeUnit.SECONDS));
            }

            Set<UUID> distinct = new HashSet<>();
            all.forEach(run -> assertTrue(distinct.add(run.getId()),
                    "stage " + run.getId() + " was claimed by more than one instance"));

            assertFalse(all.isEmpty(), "at least some stages should have been claimed");
            all.forEach(run -> assertEquals(StageStatus.RUNNING, run.getStatus()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateEnqueueForTheSameWaveAndStageIsRejected() {
        // The unique constraint on idempotency_key is what turns a double enqueue into
        // a constraint violation rather than duplicated work.
        WaveId waveId = givenWave();
        stageRuns.save(StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3));

        assertThrows(Exception.class,
                () -> stageRuns.save(StageRun.enqueue(waveId, SdlcStage.SPEC, "{}", 3)));
    }

    private WaveId givenWave() {
        Wave wave = new Wave(WaveId.generate(), "it-" + System.nanoTime(),
                new WaveContext("EPE", null, null, "English"));
        waves.save(wave);
        return wave.getId();
    }
}
