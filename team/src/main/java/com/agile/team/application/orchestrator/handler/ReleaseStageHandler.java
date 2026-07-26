package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.ReleaseAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.domain.artifact.DeployVerdict;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Release agent as a durable stage.
 * <p>
 * RELEASE carries the PRODUCTION_DEPLOY gate, which ships as {@code REQUIRED}. This
 * handler deploys to the <em>pre-production</em> environment; the gate then decides
 * whether a human releases it further. That ordering is the point of the maturity
 * ladder in SDLC_AGENT_PLAN.md §2.6: the agent may act where the blast radius is
 * recoverable, and asks where it is not.
 */
@Component
public class ReleaseStageHandler implements StageHandler {

    private final ReleaseAgent releaseAgent;
    private final ArtifactCodec codec;
    private final String defaultEnvironment;

    public ReleaseStageHandler(ReleaseAgent releaseAgent,
                               ArtifactCodec codec,
                               @Value("${sdlc.deployment.default-environment:staging}") String defaultEnvironment) {
        this.releaseAgent = releaseAgent;
        this.codec = codec;
        this.defaultEnvironment = defaultEnvironment;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.RELEASE;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        String application = wave.getContext().hasGitLabProject()
                ? lastSegment(wave.getContext().gitLabProject())
                : wave.getName();

        AgentOutcome<DeployVerdict> outcome = releaseAgent.run(new ReleaseAgent.ReleaseRequest(
                wave.getId().value().toString(),
                application,
                defaultEnvironment,
                resolveRef(run, wave)));

        List<String> notes = new ArrayList<>(outcome.notes());
        DeployVerdict verdict = outcome.artifact();

        if (!verdict.deployed()) {
            // Deployment refusals and failures both fail the stage. A refusal will not
            // become an approval on retry, but recording it as a failure keeps the
            // wave visibly stopped rather than quietly finished.
            throw new IllegalStateException("Deployment did not happen: " + verdict.summary());
        }

        notes.add("Deployed to %s, rollback reference %s"
                .formatted(verdict.environment(), verdict.rollbackRef()));
        return StageOutcome.completed(codec.write(verdict), outcome.usage(), notes);
    }

    private String resolveRef(StageRun run, Wave wave) {
        var reviewed = codec.read(run.getInputArtifact(),
                com.agile.team.domain.artifact.ReviewVerdict.class);
        // The merged ref would come from the merge; until the Developer stage targets a
        // real repository the wave's default branch is the honest answer.
        return reviewed != null ? "main" : "main";
    }

    private String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
