package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.SpecAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Spec agent as a durable stage.
 * <p>
 * The handler is the seam between the agent (a pure function of its inputs) and the
 * pipeline (persistence, gates, retry). Keeping the agent free of both is what lets
 * it be unit-tested in isolation and re-run safely on retry.
 */
@Component
public class SpecStageHandler implements StageHandler {

    private final SpecAgent specAgent;
    private final WaveRepository waves;
    private final ArtifactCodec codec;

    public SpecStageHandler(SpecAgent specAgent, WaveRepository waves, ArtifactCodec codec) {
        this.specAgent = specAgent;
        this.waves = waves;
        this.codec = codec;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.SPEC;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        SpecStageInput input = codec.read(run.getInputArtifact(), SpecStageInput.class);
        if (input == null) {
            throw new IllegalStateException("SPEC stage has no input artifact");
        }

        AgentOutcome<SpecDraft> outcome = specAgent.run(new SpecAgent.SpecAgentRequest(
                wave.getId().value().toString(),
                input.taskDescription(),
                input.keyword(),
                wave.getContext()));

        List<String> notes = new ArrayList<>(outcome.notes());

        // A retry must not append a second specification to the wave. The stage is
        // idempotent in effect: re-running replaces the draft rather than stacking.
        if (wave.getSpecifications().isEmpty()) {
            wave.addSpecification(Specification.fromDraft(
                    SpecificationId.generate(), outcome.artifact(), describeSource(wave, input)));
        } else {
            Specification existing = wave.getSpecifications().get(0);
            if (!existing.getStatus().isTerminal()) {
                existing.applyEdit(outcome.artifact(), "SPEC_AGENT_RETRY");
                notes.add("Replaced the previous draft on retry rather than adding a second specification");
            }
        }
        waves.save(wave);

        return StageOutcome.completed(codec.write(outcome.artifact()), outcome.usage(), notes);
    }

    private String describeSource(Wave wave, SpecStageInput input) {
        if (wave.getContext().hasConfluenceSpace() && input.keyword() != null) {
            return wave.getContext().confluenceSpaceKey() + ":" + input.keyword();
        }
        return "intent-only";
    }

    /** Input artifact for the SPEC stage. */
    public record SpecStageInput(String taskDescription, String keyword) {
    }
}
