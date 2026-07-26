package com.agile.team.infrastructure.persistence;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises stage artifacts to and from the JSON stored on {@code stage_run}.
 * <p>
 * Handoffs carry <strong>typed artifacts</strong>, not prose. That is what makes the
 * transport swappable later — SDLC_AGENT_PLAN.md §3.3 asks for the internal contracts
 * to map cleanly onto an A2A task artifact if a stage is ever externalised, so moving
 * a stage out of process becomes a transport change rather than a redesign.
 */
@Component
public class ArtifactCodec {

    private final JsonMapper jsonMapper;

    public ArtifactCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String write(Object artifact) {
        if (artifact == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(artifact);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to serialise artifact " + artifact.getClass().getSimpleName(), e);
        }
    }

    /**
     * @return the parsed artifact, or null when {@code json} is absent
     * @throws IllegalStateException when the stored JSON no longer matches the type —
     *         a real possibility across a deployment that changed an artifact shape,
     *         and better surfaced than silently defaulted
     */
    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Stored artifact could not be read as " + type.getSimpleName()
                            + " — the artifact shape may have changed since it was written", e);
        }
    }
}
