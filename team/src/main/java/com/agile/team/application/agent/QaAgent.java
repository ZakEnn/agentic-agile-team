package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.Implementation;
import com.agile.team.domain.artifact.QaVerdict;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.CodeExecutor;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.LlmGateway.LlmRequest;
import com.agile.team.domain.port.LlmGateway.LlmResult;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
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
 * The QA agent: proves each acceptance criterion holds, from a real test run.
 * <p>
 * The division of labour is the point. The <em>runner</em> decides whether the suite
 * is green — an exit code, not an opinion. The <em>model</em> maps individual tests
 * to individual acceptance criteria, which is genuine judgment a tool cannot do. The
 * overall verdict is then computed from both by
 * {@link QaVerdict#from(boolean, List, List, String, String)}.
 * <p>
 * The handler this replaces asked a model "did it pass?" and matched
 * {@code contains("passed") || contains("accepted")} on the reply.
 */
@Component
public class QaAgent {

    private static final Logger log = LoggerFactory.getLogger(QaAgent.class);
    private static final int OUTPUT_CHARS = 12_000;

    private static final String SYSTEM_PROMPT = """
            You are the QA engineer in an automated software delivery pipeline.

            Your job is to decide, for each acceptance criterion, whether a test in
            the run you are shown actually demonstrates it. A green suite that does
            not exercise a criterion is not evidence for that criterion, and saying so
            is the most valuable thing you do — an unverified criterion reaching
            production is exactly the failure this stage exists to prevent.

            You do not state an overall verdict. It is computed from your per-criterion
            results and the runner's exit code.
            """;

    private final LlmGateway llmGateway;
    private final CodeExecutor codeExecutor;
    private final PromptTemplates promptTemplates;
    private final List<String> testCommand;
    private final long commandTimeoutSeconds;

    public QaAgent(LlmGateway llmGateway,
                   CodeExecutor codeExecutor,
                   PromptTemplates promptTemplates,
                   @Value("${sdlc.build.test-command:mvn,-q,-B,test}") String testCommand,
                   @Value("${sdlc.build.timeout-seconds:900}") long commandTimeoutSeconds) {
        this.llmGateway = llmGateway;
        this.codeExecutor = codeExecutor;
        this.promptTemplates = promptTemplates;
        this.testCommand = Arrays.stream(testCommand.split(",")).map(String::trim).toList();
        this.commandTimeoutSeconds = commandTimeoutSeconds;
    }

    public AgentOutcome<QaVerdict> run(QaRequest request) {
        List<String> notes = new ArrayList<>();

        CodeExecutor.ExecutionResult tests = codeExecutor.run(request.workspaceId(),
                new CodeExecutor.ExecutionRequest(testCommand, commandTimeoutSeconds, Map.of()));

        boolean suiteGreen = tests.succeeded();
        notes.add("Test suite exited %d in %dms%s".formatted(
                tests.exitCode(), tests.durationMs(), tests.timedOut() ? " (timed out)" : ""));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("acceptanceCriteria", numbered(request.spec().acceptanceCriteria()));
        values.put("implementationSummary", request.implementation().summary());
        values.put("filesChanged", bullets(request.implementation().filesChanged()));
        values.put("testExitCode", String.valueOf(tests.exitCode()));
        values.put("testOutput", tests.tail(OUTPUT_CHARS));

        LlmResult<QaAssessment> result = llmGateway.generate(
                new LlmRequest(AgentRole.QA, SYSTEM_PROMPT,
                        promptTemplates.render("verify", values),
                        request.waveId(), SdlcStage.VERIFY.name()),
                QaAssessment.class);

        QaAssessment assessment = result.value();
        QaVerdict verdict = QaVerdict.from(
                suiteGreen,
                assessment.perCriterion(),
                assessment.failingTests(),
                assessment.coverageNote(),
                assessment.summary());

        if (!verdict.unverified().isEmpty()) {
            notes.add("Unverified criteria: " + verdict.unverified().stream()
                    .map(QaVerdict.CriterionResult::criterion).toList());
        }
        if (suiteGreen && !verdict.passed()) {
            // Worth stating plainly, because it is the case people misread.
            notes.add("Test suite is green but the verdict is FAILED: "
                    + "at least one acceptance criterion has no test demonstrating it");
        }

        log.info("[VERIFY] wave={} suiteGreen={} verified={}/{} passed={} tokens={}",
                request.waveId(), suiteGreen,
                verdict.perCriterion().size() - verdict.unverified().size(),
                verdict.perCriterion().size(), verdict.passed(), result.usage().total());

        return new AgentOutcome<>(verdict, result.usage(), notes);
    }

    private String numbered(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            sb.append(i + 1).append(". ").append(values.get(i)).append('\n');
        }
        return sb.toString();
    }

    private String bullets(List<String> values) {
        return values.stream().map(v -> "- " + v).reduce((a, b) -> a + "\n" + b).orElse("- (none)");
    }

    /**
     * What the model returns. Deliberately <em>not</em> a {@link QaVerdict}: it has no
     * {@code passed} field, so the model has no way to assert an overall outcome.
     */
    public record QaAssessment(
            List<QaVerdict.CriterionResult> perCriterion,
            List<String> failingTests,
            String coverageNote,
            String summary
    ) {
        public QaAssessment {
            perCriterion = perCriterion == null ? List.of() : List.copyOf(perCriterion);
            failingTests = failingTests == null ? List.of() : List.copyOf(failingTests);
        }
    }

    /**
     * @param waveId         correlation id
     * @param workspaceId    workspace holding the implementation
     * @param spec           the approved specification, source of the criteria
     * @param implementation what the Developer stage produced
     */
    public record QaRequest(
            String waveId,
            String workspaceId,
            SpecDraft spec,
            Implementation implementation
    ) {
        public QaRequest {
            if (spec == null) throw new IllegalArgumentException("spec must not be null");
            if (implementation == null) throw new IllegalArgumentException("implementation must not be null");
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("workspaceId must not be blank");
            }
        }
    }
}
