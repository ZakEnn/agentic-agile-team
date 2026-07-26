package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.port.*;
import com.agile.team.domain.port.LlmGateway.LlmRequest;
import com.agile.team.domain.port.LlmGateway.LlmResult;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Reviewer agent — the merged product of both original projects.
 * <p>
 * From {@code reviewer-agent}: the working RAG pipeline (real GitLab diff, real Jira
 * context, real SonarQube gate, a versioned prompt template, structured output).
 * From {@code team}: the governance semantics ({@code review-criteria} as a
 * versioned, CODEOWNERS-protected skill, and {@code ReviewGate}).
 * <p>
 * Neither project had both. The combination is the one genuinely differentiated
 * capability in this system: an auditable review whose approval decision is computed
 * from classified findings rather than asserted by a model.
 */
@Component
public class ReviewerAgent {

    private static final Logger log = LoggerFactory.getLogger(ReviewerAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are the code reviewer in an automated software delivery pipeline.

            You classify findings. You never decide the outcome: the pipeline computes
            approval from the severities you assign, using the taxonomy below. That
            means an inaccurate severity is not a matter of tone — it directly moves a
            merge gate.

            Precision matters more than recall here. A reviewer that reports plausible
            non-problems trains its team to ignore it, and an ignored reviewer catches
            nothing at all.
            """;

    private final GitLabPort gitLabPort;
    private final JiraPort jiraPort;
    private final SonarQubePort sonarQubePort;
    private final LlmGateway llmGateway;
    private final SkillContextResolver skillContextResolver;
    private final PromptTemplates promptTemplates;

    public ReviewerAgent(GitLabPort gitLabPort,
                         JiraPort jiraPort,
                         SonarQubePort sonarQubePort,
                         LlmGateway llmGateway,
                         SkillContextResolver skillContextResolver,
                         PromptTemplates promptTemplates) {
        this.gitLabPort = gitLabPort;
        this.jiraPort = jiraPort;
        this.sonarQubePort = sonarQubePort;
        this.llmGateway = llmGateway;
        this.skillContextResolver = skillContextResolver;
        this.promptTemplates = promptTemplates;
    }

    public AgentOutcome<ReviewVerdict> run(ReviewRequest request) {
        List<String> notes = new ArrayList<>();

        MergeRequestSnapshot mr = gitLabPort
                .fetchMergeRequest(request.projectId(), request.mergeRequestIid())
                .orElseThrow(() -> new IllegalStateException(
                        "Merge request not found: " + request.projectId() + "!" + request.mergeRequestIid()));

        if (!mr.hasDiff()) {
            notes.add("Merge request has no diff content — review is based on metadata only");
        }

        String jiraContext = resolveJiraContext(mr, notes);
        CodeQualityScore qualityScore = resolveQualityScore(request, notes);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("projectId", request.projectId());
        values.put("mergeRequestIid", request.mergeRequestIid());
        values.put("title", mr.title());
        values.put("author", mr.authorName() != null ? mr.authorName() : "unknown");
        values.put("sourceBranch", mr.sourceBranch() != null ? mr.sourceBranch() : "unknown");
        values.put("targetBranch", mr.targetBranch() != null ? mr.targetBranch() : "unknown");
        values.put("description", mr.description());
        values.put("jiraContext", jiraContext);
        values.put("qualityContext", describeQuality(qualityScore));
        values.put("diff", mr.hasDiff() ? mr.diff() : "(no diff available)");

        String userPrompt = promptTemplates.render("review", values);
        String systemPrompt = SYSTEM_PROMPT
                + skillContextResolver.resolveGovernanceContext(AgentRole.REVIEWER);

        LlmResult<ReviewVerdict> result = llmGateway.generate(
                new LlmRequest(AgentRole.REVIEWER, systemPrompt, userPrompt,
                        request.waveId(), SdlcStage.REVIEW.name()),
                ReviewVerdict.class);

        // The real quality gate and the governance provenance are attached by the
        // system, not taken from the model. The model cannot report a passing gate
        // it did not observe, and the decision remains reproducible because the exact
        // governance skills that produced it are recorded alongside it.
        List<String> governanceSkills = skillContextResolver.governanceSkillNames(AgentRole.REVIEWER);
        ReviewVerdict verdict = result.value()
                .withQualityScore(qualityScore)
                .withGovernanceSkills(governanceSkills);

        notes.add("Review produced %d finding(s): %d blocking".formatted(
                verdict.findings().size(), verdict.blockingFindings().size()));
        if (governanceSkills.isEmpty()) {
            // Worth shouting about: without review-criteria the severities have no
            // agreed meaning, so the gate is being driven by an unstated taxonomy.
            notes.add("WARNING: no governance skill applied to this review — "
                    + "severity classification is ungoverned");
            log.warn("[REVIEW] wave={} produced a verdict with no governance skill applied",
                    request.waveId());
        }

        log.info("[REVIEW] wave={} mr={}!{} findings={} blocking={} gate={} tokens={}",
                request.waveId(), request.projectId(), request.mergeRequestIid(),
                verdict.findings().size(), verdict.blockingFindings().size(),
                qualityScore.status(), result.usage().total());

        return new AgentOutcome<>(verdict, result.usage(), notes);
    }

    private String resolveJiraContext(MergeRequestSnapshot mr, List<String> notes) {
        if (!mr.hasJiraKey()) {
            notes.add("No Jira key found in the MR title or description — "
                    + "reviewed against the diff alone, not against a stated requirement");
            return "No linked issue. Judge the change on its own terms.";
        }
        Optional<JiraIssueSnapshot> issue = jiraPort.fetchIssue(mr.jiraKey());
        if (issue.isEmpty()) {
            notes.add("Jira issue " + mr.jiraKey() + " could not be fetched — "
                    + "reviewed without requirement context");
            return "Issue " + mr.jiraKey() + " referenced but not retrievable.";
        }

        JiraIssueSnapshot snapshot = issue.get();
        StringBuilder sb = new StringBuilder();
        sb.append(snapshot.key()).append(" — ").append(snapshot.summary()).append('\n');
        sb.append("Status: ").append(snapshot.status()).append('\n');
        if (snapshot.hasAcceptanceCriteria()) {
            sb.append("Acceptance criteria:\n");
            snapshot.acceptanceCriteria().forEach(c -> sb.append("  - ").append(c).append('\n'));
        } else {
            sb.append("No acceptance criteria stated on the issue.\n");
            notes.add("Jira issue " + snapshot.key() + " has no acceptance criteria");
        }
        if (!snapshot.description().isBlank()) {
            sb.append("Description:\n").append(snapshot.description()).append('\n');
        }
        return sb.toString();
    }

    private CodeQualityScore resolveQualityScore(ReviewRequest request, List<String> notes) {
        if (request.sonarProjectKey() == null || request.sonarProjectKey().isBlank()) {
            notes.add("No SonarQube project key supplied — quality gate is UNKNOWN, "
                    + "which does not pass the ReviewGate");
            return CodeQualityScore.unknown();
        }
        CodeQualityScore score = sonarQubePort.getQualityGateStatus(request.sonarProjectKey())
                .orElse(CodeQualityScore.unknown());
        if (score.isUnknown()) {
            notes.add("SonarQube quality gate could not be evaluated for "
                    + request.sonarProjectKey() + " — treated as a failure, not a pass");
        }
        return score;
    }

    private String describeQuality(CodeQualityScore score) {
        return switch (score.status()) {
            case PASSED -> "SonarQube quality gate PASSED (%.0f%% of conditions met)."
                    .formatted(score.score());
            case FAILED -> "SonarQube quality gate FAILED (%.0f%% of conditions met). "
                    .formatted(score.score())
                    + "The merge is already blocked by the automated gate; focus on what it cannot see.";
            case UNKNOWN -> "SonarQube quality gate could not be evaluated. "
                    + "Do not assume the code is clean.";
        };
    }

    /**
     * @param waveId          correlation id
     * @param projectId       GitLab project path or id
     * @param mergeRequestIid MR internal id
     * @param sonarProjectKey SonarQube project key; when absent the gate is UNKNOWN
     */
    public record ReviewRequest(
            String waveId,
            String projectId,
            String mergeRequestIid,
            String sonarProjectKey
    ) {
        public ReviewRequest {
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId must not be blank");
            }
            if (mergeRequestIid == null || mergeRequestIid.isBlank()) {
                throw new IllegalArgumentException("mergeRequestIid must not be blank");
            }
        }
    }
}
