package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ChangePlan;
import com.agile.team.domain.artifact.Implementation;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.CodeExecutor;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.LlmGateway.LlmRequest;
import com.agile.team.domain.port.LlmGateway.LlmResult;
import com.agile.team.domain.port.LlmGateway.TokenUsage;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Developer agent: turns an approved specification into a change that compiles
 * and passes its tests.
 * <p>
 * <strong>The honest boundary.</strong> SDLC_AGENT_PLAN.md §6 names this as the
 * highest-risk part of the whole system, and the one thing neither original project
 * had ever done. What is implemented here is the part that is genuinely verifiable
 * without a vendor integration: the model proposes a {@link ChangePlan}, the
 * executor writes it into a confined workspace, and a <em>real</em> build and test
 * run decide whether the stage succeeded. A failing build throws with its output
 * attached, so the stage retries with the compiler's feedback rather than the
 * model's optimism.
 * <p>
 * Delegating to a purpose-built coding agent (Claude Code, OpenHands) is a swap of
 * {@link CodeExecutor} plus a credential — see DECISIONS.md D-008 and the BLOCKED
 * entry in IMPLEMENTATION_LOG.md.
 */
@Component
public class DeveloperAgent {

    private static final Logger log = LoggerFactory.getLogger(DeveloperAgent.class);
    private static final int FEEDBACK_CHARS = 6_000;

    private static final String SYSTEM_PROMPT = """
            You are the developer in an automated software delivery pipeline.

            You produce complete file contents, never diffs or fragments. Every file
            you return will be written to disk exactly as you give it, then compiled
            and tested. A file that is truncated, elided with "... rest unchanged ...",
            or missing an import will simply fail to build.

            You are judged by the compiler and the test suite, not by your description
            of the change. Prefer the smallest change that satisfies the specification,
            and include tests for the acceptance criteria you are implementing.
            """;

    private final LlmGateway llmGateway;
    private final CodeExecutor codeExecutor;
    private final SkillContextResolver skillContextResolver;
    private final PromptTemplates promptTemplates;
    private final List<String> buildCommand;
    private final List<String> testCommand;
    private final long commandTimeoutSeconds;

    public DeveloperAgent(LlmGateway llmGateway,
                          CodeExecutor codeExecutor,
                          SkillContextResolver skillContextResolver,
                          PromptTemplates promptTemplates,
                          @Value("${sdlc.build.command:mvn,-q,-B,compile}") String buildCommand,
                          @Value("${sdlc.build.test-command:mvn,-q,-B,test}") String testCommand,
                          @Value("${sdlc.build.timeout-seconds:900}") long commandTimeoutSeconds) {
        this.llmGateway = llmGateway;
        this.codeExecutor = codeExecutor;
        this.skillContextResolver = skillContextResolver;
        this.promptTemplates = promptTemplates;
        this.buildCommand = Arrays.stream(buildCommand.split(",")).map(String::trim).toList();
        this.testCommand = Arrays.stream(testCommand.split(",")).map(String::trim).toList();
        this.commandTimeoutSeconds = commandTimeoutSeconds;
    }

    /**
     * The caller supplies the workspace, because the caller knows its lifetime: the
     * BUILD stage keeps it so VERIFY can run tests against the very build that was
     * produced, rather than a re-creation of it.
     */
    public AgentOutcome<Implementation> run(DeveloperRequest request) {
        List<String> notes = new ArrayList<>();
        String workspaceId = request.workspaceId();

        LlmResult<ChangePlan> planned = planChange(request, notes);
        ChangePlan plan = planned.value();

        codeExecutor.applyChanges(workspaceId, plan.changes().stream()
                .map(edit -> new CodeExecutor.FileChange(edit.path(), edit.content()))
                .toList());
        notes.add("Applied %d file change(s): %s"
                .formatted(plan.changes().size(), plan.changedPaths()));

        Implementation implementation = buildAndTest(
                workspaceId, request, plan, planned.usage(), notes);

        return new AgentOutcome<>(implementation, planned.usage(), notes);
    }

    private LlmResult<ChangePlan> planChange(DeveloperRequest request, List<String> notes) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("summary", request.spec().summary());
        values.put("description", request.spec().description());
        values.put("acceptanceCriteria", bullets(request.spec().acceptanceCriteria()));
        values.put("outOfScope", request.spec().outOfScope().isEmpty()
                ? "None stated." : bullets(request.spec().outOfScope()));
        values.put("designNote", request.designNote() != null ? request.designNote() : "None supplied.");
        values.put("previousFailure", request.previousFailure() != null
                ? request.previousFailure()
                : "This is the first attempt.");

        if (request.previousFailure() != null) {
            notes.add("Retrying with the previous build failure fed back to the model");
        }

        String systemPrompt = SYSTEM_PROMPT
                + skillContextResolver.resolveGovernanceContext(AgentRole.DEV);

        return llmGateway.generate(
                new LlmRequest(AgentRole.DEV, systemPrompt,
                        promptTemplates.render("develop", values),
                        request.waveId(), SdlcStage.BUILD.name()),
                ChangePlan.class);
    }

    private Implementation buildAndTest(String workspaceId, DeveloperRequest request,
                                        ChangePlan plan, TokenUsage usage, List<String> notes) {
        CodeExecutor.ExecutionResult build = codeExecutor.run(workspaceId,
                new CodeExecutor.ExecutionRequest(buildCommand, commandTimeoutSeconds, Map.of()));
        notes.add("Build exited %d in %dms".formatted(build.exitCode(), build.durationMs()));

        if (!build.succeeded()) {
            // Throwing rather than returning a failed Implementation is deliberate:
            // the stage machine's retry loop feeds this message back into the next
            // attempt, which is the only mechanism by which the agent can improve.
            throw new BuildFailedException(
                    "Build failed (exit " + build.exitCode() + (build.timedOut() ? ", timed out" : "") + ")",
                    build.tail(FEEDBACK_CHARS));
        }

        CodeExecutor.ExecutionResult tests = codeExecutor.run(workspaceId,
                new CodeExecutor.ExecutionRequest(testCommand, commandTimeoutSeconds, Map.of()));
        notes.add("Tests exited %d in %dms".formatted(tests.exitCode(), tests.durationMs()));

        if (!tests.succeeded()) {
            throw new BuildFailedException(
                    "Tests failed (exit " + tests.exitCode() + (tests.timedOut() ? ", timed out" : "") + ")",
                    tests.tail(FEEDBACK_CHARS));
        }

        log.info("[BUILD] wave={} files={} tokens={}",
                request.waveId(), plan.changes().size(), usage.total());

        return new Implementation(
                request.branchName(),
                null,
                plan.changedPaths(),
                true,
                true,
                plan.rationale(),
                tests.tail(2_000));
    }

    private String bullets(List<String> values) {
        return values.stream().map(v -> "- " + v).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /**
     * Carries the compiler's own words back to the retry.
     * <p>
     * The message the model sees on retry is the build output, not a paraphrase.
     */
    public static class BuildFailedException extends RuntimeException {
        private final String output;

        public BuildFailedException(String message, String output) {
            super(message + "\n\n" + output);
            this.output = output;
        }

        public String output() {
            return output;
        }
    }

    /**
     * @param waveId          correlation id
     * @param spec            the approved specification
     * @param designNote      the architect's note, when one exists
     * @param branchName      branch the change belongs on
     * @param previousFailure build output from the previous attempt, for the feedback loop
     * @param workspaceId     workspace to work in; owned by the caller
     */
    public record DeveloperRequest(
            String waveId,
            SpecDraft spec,
            String designNote,
            String branchName,
            String previousFailure,
            String workspaceId
    ) {
        public DeveloperRequest {
            if (spec == null) throw new IllegalArgumentException("spec must not be null");
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("workspaceId must not be blank");
            }
            if (branchName == null || branchName.isBlank()) {
                branchName = "feature/wave-" + (waveId != null ? waveId : "unknown");
            }
        }
    }
}
