package com.agile.team.domain.port;

import java.time.Instant;
import java.util.List;

/**
 * Durable record of which governance skills produced which decisions.
 * <p>
 * A port rather than a direct repository call so the use case stays free of
 * persistence concerns and can be unit-tested with an in-memory implementation.
 */
public interface SkillAuditPort {

    void record(SkillUsage usage);

    List<SkillUsage> findByWave(String waveId);

    List<SkillUsage> findAll();

    /**
     * @param skillName which governance skill was applied
     * @param waveId    the wave it was applied to, may be null for ad-hoc reviews
     * @param agentId   which agent applied it
     * @param usedAt    when
     */
    record SkillUsage(String skillName, String waveId, String agentId, Instant usedAt) {
        public SkillUsage {
            if (skillName == null || skillName.isBlank()) {
                throw new IllegalArgumentException("skillName must not be blank");
            }
            if (usedAt == null) usedAt = Instant.now();
        }
    }
}
