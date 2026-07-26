package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ArtifactValidationException;
import com.agile.team.domain.artifact.DesignNote;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.LlmGateway.LlmRequest;
import com.agile.team.domain.port.LlmGateway.LlmResult;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Architect agent: decides where and how, before code exists.
 * <p>
 * The stage exists for one specific reason. A Developer agent handed only a
 * specification will invent a place to put the change, and an invented module
 * becomes an invented file, which becomes a build failure two stages later with no
 * obvious cause. Naming the impacted modules up front — and
 * <strong>verifying them against the real repository tree</strong> — converts that
 * silent failure into a loud one at the cheapest possible moment.
 */
@Component
public class ArchitectAgent {

    private static final Logger log = LoggerFactory.getLogger(ArchitectAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are the architect in an automated software delivery pipeline.

            You decide where a change belongs and how it should be made. You do not
            write the implementation.

            Name only modules and paths that appear in the repository listing you are
            given. Every name you return is checked against that listing, and a name
            that is not in it fails this stage. If the listing does not contain a
            suitable place for part of the change, say so in your approach rather
            than inventing a plausible path.
            """;

    private final LlmGateway llmGateway;
    private final PromptTemplates promptTemplates;

    public ArchitectAgent(LlmGateway llmGateway, PromptTemplates promptTemplates) {
        this.llmGateway = llmGateway;
        this.promptTemplates = promptTemplates;
    }

    public AgentOutcome<DesignNote> run(ArchitectRequest request) {
        List<String> notes = new ArrayList<>();

        Map<String, String> values = new LinkedHashMap<>();
        values.put("summary", request.spec().summary());
        values.put("description", request.spec().description());
        values.put("acceptanceCriteria", bullets(request.spec().acceptanceCriteria()));
        values.put("outOfScope", request.spec().outOfScope().isEmpty()
                ? "None stated." : bullets(request.spec().outOfScope()));
        values.put("repositoryListing", request.repositoryPaths().isEmpty()
                ? "(repository listing unavailable)"
                : bullets(request.repositoryPaths()));

        LlmResult<DesignNote> result = llmGateway.generate(
                new LlmRequest(AgentRole.ARCHITECT, SYSTEM_PROMPT,
                        promptTemplates.render("design", values),
                        request.waveId(), SdlcStage.DESIGN.name()),
                DesignNote.class);

        DesignNote note = result.value();
        verifyModules(note, request, notes);

        if (note.adrRequired()) {
            notes.add("Architect flagged this decision as warranting an ADR");
        }
        notes.add("Design impacts %d module(s): %s"
                .formatted(note.impactedModules().size(), note.impactedModules()));

        log.info("[DESIGN] wave={} modules={} tokens={}",
                request.waveId(), note.impactedModules().size(), result.usage().total());

        return new AgentOutcome<>(note, result.usage(), notes);
    }

    /**
     * Check every named module against the real repository listing.
     * <p>
     * Throws rather than warns when verification is required: an unverified module
     * name is exactly the input that produces an expensive, confusing failure later.
     * Failing here sends the stage back for another attempt with the reason attached.
     */
    private void verifyModules(DesignNote note, ArchitectRequest request, List<String> notes) {
        if (request.repositoryPaths().isEmpty()) {
            if (request.requireVerification()) {
                throw new IllegalStateException(
                        "Cannot verify impacted modules: the repository listing is unavailable. "
                                + "Set sdlc.design.require-module-verification=false to accept "
                                + "unverified designs, understanding that a hallucinated module "
                                + "will surface as a build failure two stages later.");
            }
            notes.add("WARNING: repository listing unavailable — impacted modules are UNVERIFIED");
            return;
        }

        List<String> unknown = note.impactedModules().stream()
                .filter(module -> !existsIn(module, request.repositoryPaths()))
                .toList();

        if (!unknown.isEmpty()) {
            throw new ArtifactValidationException(
                    "Design names module(s) that do not exist in the repository: " + unknown
                            + ". Choose from the supplied listing or state that no suitable "
                            + "location exists.");
        }
        notes.add("All %d impacted module(s) verified against the repository tree"
                .formatted(note.impactedModules().size()));
    }

    /**
     * A named module matches if any real path equals it or sits beneath it. This
     * accepts both "a directory" and "a specific file" as legitimate answers without
     * accepting a name that appears nowhere.
     */
    private boolean existsIn(String module, List<String> repositoryPaths) {
        String needle = module.replace('\\', '/').toLowerCase(Locale.ROOT);
        return repositoryPaths.stream()
                .map(p -> p.replace('\\', '/').toLowerCase(Locale.ROOT))
                .anyMatch(path -> path.equals(needle)
                        || path.startsWith(needle.endsWith("/") ? needle : needle + "/")
                        || path.endsWith("/" + needle));
    }

    private String bullets(List<String> values) {
        return values.stream().map(v -> "- " + v).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /**
     * @param waveId              correlation id
     * @param spec                the approved specification
     * @param repositoryPaths     real paths from the repository, used to verify the design
     * @param requireVerification whether an unavailable listing fails the stage
     */
    public record ArchitectRequest(
            String waveId,
            SpecDraft spec,
            List<String> repositoryPaths,
            boolean requireVerification
    ) {
        public ArchitectRequest {
            if (spec == null) throw new IllegalArgumentException("spec must not be null");
            repositoryPaths = repositoryPaths == null ? List.of() : List.copyOf(repositoryPaths);
        }
    }
}
