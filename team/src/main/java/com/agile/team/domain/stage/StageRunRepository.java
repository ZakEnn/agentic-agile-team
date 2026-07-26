package com.agile.team.domain.stage;

import com.agile.team.domain.wave.WaveId;

import java.util.List;
import java.util.Optional;

public interface StageRunRepository {

    StageRun save(StageRun stageRun);

    Optional<StageRun> findById(java.util.UUID id);

    Optional<StageRun> findByWaveAndStage(WaveId waveId, SdlcStage stage);

    List<StageRun> findByWave(WaveId waveId);

    /**
     * Atomically claim up to {@code limit} due stages for this instance.
     * <p>
     * The claim must be safe against other instances running the same query
     * concurrently — on Cloud Foundry the shared datasource is the only
     * coordination primitive available.
     *
     * @param instanceId identity of the claiming instance (CF_INSTANCE_GUID)
     * @return the stages now owned by this instance, in RUNNING state
     */
    List<StageRun> claimDueStages(String instanceId, int limit);

    /** Move RETRYING stages whose backoff has elapsed back to PENDING. */
    int releaseDueRetries();

    /**
     * Reclaim stages left RUNNING by an instance that died, so their wave is not
     * blocked forever.
     *
     * @param olderThanSeconds how long a claim may be held before it is considered lost
     */
    int reclaimStaleRunning(long olderThanSeconds);
}
