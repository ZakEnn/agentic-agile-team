package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.ArchitectAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.domain.artifact.DesignNote;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.GitLabPort;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.infrastructure.config.SdlcProperties;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Runs the Architect agent, with its impacted modules checked against the real repo. */
@Component
public class DesignStageHandler implements StageHandler {

    private final ArchitectAgent architectAgent;
    private final GitLabPort gitLabPort;
    private final SdlcProperties properties;
    private final ArtifactCodec codec;

    public DesignStageHandler(ArchitectAgent architectAgent,
                              GitLabPort gitLabPort,
                              SdlcProperties properties,
                              ArtifactCodec codec) {
        this.architectAgent = architectAgent;
        this.gitLabPort = gitLabPort;
        this.properties = properties;
        this.codec = codec;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.DESIGN;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        SpecDraft spec = resolveSpec(run, wave);
        List<String> repositoryPaths = listRepositoryPaths(wave);

        AgentOutcome<DesignNote> outcome = architectAgent.run(new ArchitectAgent.ArchitectRequest(
                wave.getId().value().toString(),
                spec,
                repositoryPaths,
                properties.getDesign().isRequireModuleVerification()));

        List<String> notes = new ArrayList<>(outcome.notes());

        // BUILD needs the specification, and the design note travels alongside it as
        // context rather than replacing it.
        return StageOutcome.completed(
                codec.write(new DesignStageOutput(spec, outcome.artifact())),
                outcome.usage(), notes);
    }

    private List<String> listRepositoryPaths(Wave wave) {
        if (!wave.getContext().hasGitLabProject()) {
            return List.of();
        }
        return gitLabPort.listRepositoryPaths(wave.getContext().gitLabProject(), "main");
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
                        "DESIGN stage has no approved specification to design against"));
    }

    /** What DESIGN hands to BUILD: the spec it must satisfy and the design to follow. */
    public record DesignStageOutput(SpecDraft spec, DesignNote design) {
    }
}
