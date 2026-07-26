package com.agile.team.application.orchestrator;

import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.stage.StageRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls the stage queue and executes what it claims.
 * <p>
 * Replaces the {@code @Async @EventListener} chain. The difference that matters is
 * not the polling — it is that the queue is a table: work survives a restart, a
 * second instance can pick up what a dead one dropped, and the position of every
 * wave is a row you can query rather than a thread you cannot see.
 * <p>
 * {@link #pollOnce()} is public and deterministic so tests drive the real durable
 * path directly instead of sleeping and hoping.
 */
@Component
public class StageWorker {

    private static final Logger log = LoggerFactory.getLogger(StageWorker.class);

    private final StageRunRepository stageRuns;
    private final StageExecutor executor;
    private final String instanceId;
    private final int batchSize;
    private final long staleClaimSeconds;

    public StageWorker(StageRunRepository stageRuns,
                       StageExecutor executor,
                       @Value("${sdlc.worker.batch-size:5}") int batchSize,
                       @Value("${sdlc.worker.stale-claim-seconds:600}") long staleClaimSeconds) {
        this.stageRuns = stageRuns;
        this.executor = executor;
        this.batchSize = batchSize;
        this.staleClaimSeconds = staleClaimSeconds;
        this.instanceId = resolveInstanceId();
        log.info("StageWorker instance id: {}", instanceId);
    }

    @Scheduled(fixedDelayString = "${sdlc.worker.poll-interval-ms:2000}")
    public void poll() {
        try {
            pollOnce();
        } catch (Exception e) {
            // A worker that dies on an unexpected error stops the whole pipeline.
            log.error("Stage worker poll failed: {}", e.getMessage(), e);
        }
    }

    /**
     * One full cycle: recover abandoned work, release due retries, claim, execute.
     *
     * @return how many stages were executed
     */
    public int pollOnce() {
        stageRuns.reclaimStaleRunning(staleClaimSeconds);
        stageRuns.releaseDueRetries();

        List<StageRun> claimed = stageRuns.claimDueStages(instanceId, batchSize);
        if (claimed.isEmpty()) {
            return 0;
        }
        log.debug("Claimed {} stage(s)", claimed.size());
        claimed.forEach(executor::execute);
        return claimed.size();
    }

    /**
     * Drain the queue until nothing more is runnable.
     * <p>
     * Used by tests and by the synchronous start path. Bounded so a stage that
     * re-enqueues itself cannot spin forever.
     */
    public int drain(int maxCycles) {
        int executed = 0;
        for (int i = 0; i < maxCycles; i++) {
            int done = pollOnce();
            if (done == 0) {
                break;
            }
            executed += done;
        }
        return executed;
    }

    public String instanceId() {
        return instanceId;
    }

    /**
     * Identify this instance.
     * <p>
     * {@code CF_INSTANCE_GUID} rather than {@code CF_INSTANCE_INDEX}: an index can be
     * reused after a restage, which would make two different processes look like the
     * same claimant and make an abandoned claim indistinguishable from a live one.
     */
    private static String resolveInstanceId() {
        String guid = System.getenv("CF_INSTANCE_GUID");
        if (guid != null && !guid.isBlank()) {
            return guid;
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName()
                    + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "instance-" + java.util.UUID.randomUUID();
        }
    }
}
