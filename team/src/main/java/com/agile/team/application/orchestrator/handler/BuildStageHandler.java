package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.DeveloperAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.domain.artifact.Implementation;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Developer agent as a durable stage.
 * <p>
 * Two things worth noting. First, the workspace is <strong>kept</strong>, not
 * discarded: the VERIFY stage runs its tests in the same workspace, so the QA agent
 * is inspecting the actual build the Developer produced rather than a re-creation of
 * it. Second, the previous attempt's failure is read off the {@link StageRun} and
 * handed back to the agent — that feedback loop is the only mechanism by which a
 * retry differs from a repeat.
 */
@Component
public class BuildStageHandler implements StageHandler {

    private final DeveloperAgent developerAgent;
    private final com.agile.team.domain.port.CodeExecutor codeExecutor;
    private final ArtifactCodec codec;

    public BuildStageHandler(DeveloperAgent developerAgent,
                             com.agile.team.domain.port.CodeExecutor codeExecutor,
                             ArtifactCodec codec) {
        this.developerAgent = developerAgent;
        this.codeExecutor = codeExecutor;
        this.codec = codec;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.BUILD;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        SpecDraft spec = resolveSpec(run, wave);
        String workspaceId = codeExecutor.prepareWorkspace(wave.getId().value().toString());

        AgentOutcome<Implementation> outcome;
        try {
            outcome = developerAgent.run(new DeveloperAgent.DeveloperRequest(
                    wave.getId().value().toString(),
                    spec,
                    readDesignNote(run),
                    branchNameFor(wave, spec),
                    run.getErrorMessage(),
                    workspaceId));
        } catch (RuntimeException e) {
            // A failed attempt's workspace is dead weight; the retry gets a fresh one.
            codeExecutor.discardWorkspace(workspaceId);
            throw e;
        }

        List<String> notes = new ArrayList<>(outcome.notes());
        Implementation implementation = outcome.artifact();
        if (!implementation.isComplete()) {
            // Belt and braces: the agent already throws on a failed build, but an
            // incomplete Implementation must never be handed downstream as though it
            // were finished work.
            codeExecutor.discardWorkspace(workspaceId);
            throw new IllegalStateException(
                    "Developer agent returned an incomplete implementation: buildPassed="
                            + implementation.buildPassed() + " testsPassed=" + implementation.testsPassed());
        }

        notes.add("Implementation complete on branch " + implementation.branch());
        return StageOutcome.completed(
                codec.write(new BuildStageOutput(implementation, workspaceId)),
                outcome.usage(), notes);
    }

    private SpecDraft resolveSpec(StageRun run, Wave wave) {
        SpecDraft fromArtifact = codec.read(run.getInputArtifact(), SpecDraft.class);
        if (fromArtifact != null) {
            return fromArtifact;
        }
        return wave.getSpecifications().stream()
                .filter(Specification::isApproved)
                .findFirst()
                .map(Specification::toDraft)
                .orElseThrow(() -> new IllegalStateException(
                        "BUILD stage has no specification: the wave has no approved spec "
                                + "and the stage carries no input artifact"));
    }

    private String readDesignNote(StageRun run) {
        // The Architect agent lands in M5. Until then the design note is absent, and
        // the prompt says so explicitly rather than leaving a gap the model fills in.
        return null;
    }

    private String branchNameFor(Wave wave, SpecDraft spec) {
        String slug = spec.summary().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-$", "");
        }
        return "feature/" + wave.getId().value().toString().substring(0, 8) + "-" + slug;
    }

    /**
     * What BUILD hands to VERIFY.
     *
     * @param implementation what was produced
     * @param workspaceId    where it lives, so the tests run against the real build
     */
    public record BuildStageOutput(Implementation implementation, String workspaceId) {
    }
}
