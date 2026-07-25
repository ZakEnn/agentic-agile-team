package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ArtifactValidationException;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.LlmGateway.LlmRequest;
import com.agile.team.domain.port.LlmGateway.LlmResult;
import com.agile.team.domain.port.LlmGateway.TokenUsage;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Product/Spec agent: turns an intent plus Confluence context into a validated,
 * testable {@link SpecDraft}.
 * <p>
 * Two things distinguish this from the {@code PoAgentHandler} it replaces:
 * <ol>
 *   <li><strong>Retrieval is deterministic.</strong> Confluence is read through
 *       {@link ConfluencePort} — a real REST client — not by giving a model a search
 *       tool and hoping. The model receives text that was actually fetched.</li>
 *   <li><strong>The output is a validated type.</strong> The old handler demanded
 *       strict JSON in its prompt, then stored the raw response verbatim and passed
 *       {@code List.of()} for acceptance criteria, discarding the very thing the QA
 *       stage needs. Here the response binds to {@link SpecDraft}, whose constructor
 *       rejects a spec with no criteria.</li>
 * </ol>
 * The agent is a pure function of its inputs: it performs no persistence and
 * publishes no events, so it can be tested with a scripted gateway and a stub port.
 */
@Component
public class SpecAgent {

    private static final Logger log = LoggerFactory.getLogger(SpecAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are the Product Owner agent in an automated software delivery pipeline.

            Your job is to turn an intent and supporting documentation into a
            specification that a developer can implement and a QA agent can verify
            without asking follow-up questions.

            Rules:
            - Every acceptance criterion must be independently verifiable. Prefer
              Given/When/Then form. A criterion that cannot fail a test is not a
              criterion.
            - Base the specification on the supplied documentation. Where the
              documentation is silent, say so explicitly in the description rather
              than inventing behaviour — a plausible invention is worse than a
              stated gap, because it will be implemented.
            - State non-goals in outOfScope. Scope drift during implementation is
              cheaper to prevent here than to review later.
            """;

    private final ConfluencePort confluencePort;
    private final LlmGateway llmGateway;
    private final SkillContextResolver skillContextResolver;

    public SpecAgent(ConfluencePort confluencePort,
                     LlmGateway llmGateway,
                     SkillContextResolver skillContextResolver) {
        this.confluencePort = confluencePort;
        this.llmGateway = llmGateway;
        this.skillContextResolver = skillContextResolver;
    }

    /**
     * Produce a specification draft.
     *
     * @throws ArtifactValidationException if the model's output is not a usable spec
     * @throws LlmGateway.LlmGatewayException if generation fails
     */
    public AgentOutcome<SpecDraft> run(SpecAgentRequest request) {
        List<String> notes = new ArrayList<>();

        String documentation = retrieveDocumentation(request, notes);
        String userPrompt = buildPrompt(request, documentation);

        String systemPrompt = SYSTEM_PROMPT
                + skillContextResolver.resolveGovernanceContext(AgentRole.PO);

        LlmResult<SpecDraft> result = llmGateway.generate(
                new LlmRequest(AgentRole.PO, systemPrompt, userPrompt,
                        request.waveId(), SdlcStage.SPEC.name()),
                SpecDraft.class);

        SpecDraft draft = result.value();
        notes.add("Specification drafted with %d acceptance criteria and %d non-goals"
                .formatted(draft.acceptanceCriteria().size(), draft.outOfScope().size()));

        log.info("[SPEC] wave={} criteria={} tokens={}",
                request.waveId(), draft.acceptanceCriteria().size(), result.usage().total());

        return new AgentOutcome<>(draft, result.usage(), notes);
    }

    private String retrieveDocumentation(SpecAgentRequest request, List<String> notes) {
        WaveContext context = request.context();
        if (!context.hasConfluenceSpace() || request.keyword() == null || request.keyword().isBlank()) {
            notes.add("No Confluence space or keyword supplied — drafting from the intent alone");
            return "";
        }

        try {
            Optional<String> found = confluencePort.searchPages(
                    context.confluenceSpaceKey(), request.keyword());
            if (found.isPresent() && !found.get().isBlank()) {
                String content = found.get();
                notes.add("Retrieved %d characters from Confluence space %s for keyword '%s'"
                        .formatted(content.length(), context.confluenceSpaceKey(), request.keyword()));
                return content;
            }
            notes.add("No Confluence pages matched keyword '%s' in space %s"
                    .formatted(request.keyword(), context.confluenceSpaceKey()));
            return "";
        } catch (Exception e) {
            // Retrieval failure degrades the spec but should not fail the stage: the
            // agent can still draft from the intent, and the note makes the gap
            // visible to the human at the approval gate.
            log.warn("[SPEC] Confluence retrieval failed for wave={}: {}", request.waveId(), e.getMessage());
            notes.add("Confluence retrieval failed (" + e.getMessage()
                    + ") — drafted from the intent alone; review context carefully");
            return "";
        }
    }

    private String buildPrompt(SpecAgentRequest request, String documentation) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Produce a specification for the following intent.\n\n");
        prompt.append("## Intent\n").append(request.taskDescription()).append("\n\n");

        if (!documentation.isBlank()) {
            prompt.append("## Supporting documentation\n");
            prompt.append("Retrieved from Confluence space ")
                  .append(request.context().confluenceSpaceKey())
                  .append(" using keyword '").append(request.keyword()).append("'.\n\n");
            prompt.append(documentation).append("\n\n");
        } else {
            prompt.append("## Supporting documentation\n");
            prompt.append("None available. Note explicitly in the description which ")
                  .append("details could not be confirmed against documentation.\n\n");
        }

        prompt.append("## Output language\n");
        prompt.append("Write all prose in ").append(request.context().language()).append(".\n");

        return prompt.toString();
    }

    /**
     * Input to the spec stage.
     *
     * @param waveId          correlation id
     * @param taskDescription the human's intent
     * @param keyword         Confluence search term; optional
     * @param context         which space/project/language this wave targets
     */
    public record SpecAgentRequest(
            String waveId,
            String taskDescription,
            String keyword,
            WaveContext context
    ) {
        public SpecAgentRequest {
            if (taskDescription == null || taskDescription.isBlank()) {
                throw new IllegalArgumentException("taskDescription must not be blank");
            }
            if (context == null) {
                context = WaveContext.forSpecOnly(null, null);
            }
        }
    }
}
