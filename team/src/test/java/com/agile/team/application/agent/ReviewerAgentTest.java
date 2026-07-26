package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.port.JiraIssueSnapshot;
import com.agile.team.domain.port.MergeRequestSnapshot;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.Finding;
import com.agile.team.domain.review.Severity;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import com.agile.team.support.ScriptedLlmGateway;
import com.agile.team.support.StubToolchainPorts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewerAgentTest {

    private StubToolchainPorts.Git git;
    private StubToolchainPorts.Jira jira;
    private StubToolchainPorts.Sonar sonar;
    private ScriptedLlmGateway llm;

    private static final MergeRequestSnapshot MR = new MergeRequestSnapshot(
            "epe/epe-rating-ftth-passive", "42",
            "SCA-1234 Add retry to SFTP poller", "Implements bounded retries.",
            "feature/SCA-1234", "main", "A Developer", "SCA-1234",
            "diff --git a/SftpPoller.java b/SftpPoller.java\n+ retry logic");

    @BeforeEach
    void setUp() {
        git = new StubToolchainPorts.Git().returning(MR);
        jira = new StubToolchainPorts.Jira();
        sonar = new StubToolchainPorts.Sonar();
        llm = new ScriptedLlmGateway();
    }

    @Test
    void shouldAttachTheRealQualityGateNotWhateverTheModelClaims() {
        // The model returns a verdict with an UNKNOWN default score; the system
        // overwrites it with the score actually observed. A model cannot report a
        // passing gate it never saw.
        sonar.returning(CodeQualityScore.passing(92));
        llm.respondWith(new ReviewVerdict("Looks good", List.of()));

        ReviewVerdict verdict = agent().run(request("sonar-key")).artifact();

        assertTrue(verdict.qualityScore().passed());
        assertEquals(92, verdict.qualityScore().score(), 0.01);
    }

    @Test
    void shouldFailClosedWhenSonarQubeIsUnavailable() {
        sonar.returning(CodeQualityScore.unknown());
        llm.respondWith(new ReviewVerdict("No defects found", List.of()));

        AgentOutcome<ReviewVerdict> outcome = agent().run(request("sonar-key"));

        assertTrue(outcome.artifact().qualityScore().isUnknown());
        assertFalse(outcome.artifact().qualityScore().passed());
        assertTrue(outcome.notes().stream()
                .anyMatch(n -> n.contains("treated as a failure, not a pass")));
    }

    @Test
    void shouldReportUnknownQualityWhenNoSonarProjectKeyIsSupplied() {
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        AgentOutcome<ReviewVerdict> outcome = agent().run(request(null));

        assertTrue(outcome.artifact().qualityScore().isUnknown());
        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("No SonarQube project key")));
    }

    @Test
    void shouldComputeChangesRequestedFromABlockingFinding() {
        sonar.returning(CodeQualityScore.passing(99));
        llm.respondWith(new ReviewVerdict("Ships a hardcoded secret", List.of(
                new Finding(Severity.CRITICAL, "Config.java", 12, "Hardcoded API key", "Security"))));

        ReviewVerdict verdict = agent().run(request("sonar-key")).artifact();

        assertEquals(ApprovalStatus.CHANGES_REQUESTED, verdict.approvalStatus());
    }

    @Test
    void shouldIncludeJiraAcceptanceCriteriaInThePrompt() {
        jira.returning(new JiraIssueSnapshot("SCA-1234", "Add retry", "desc", "In Progress",
                List.of("Given a timeout, then it retries")));
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        agent().run(request("sonar-key"));

        String prompt = llm.lastRequest().userPrompt();
        assertTrue(prompt.contains("SCA-1234"));
        assertTrue(prompt.contains("Given a timeout, then it retries"),
                "the reviewer must be able to judge against the stated requirement");
    }

    @Test
    void shouldNoteWhenNoJiraKeyIsPresent() {
        MergeRequestSnapshot noKey = new MergeRequestSnapshot(
                "p", "1", "no key here", "", "b", "main", "dev", null, "diff");
        git.returning(noKey);
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        AgentOutcome<ReviewVerdict> outcome = agent().run(request("sonar-key"));

        assertTrue(outcome.notes().stream()
                .anyMatch(n -> n.contains("No Jira key found")));
    }

    @Test
    void shouldDegradeWhenJiraCannotBeFetched() {
        // jira stub returns empty by default
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        AgentOutcome<ReviewVerdict> outcome = agent().run(request("sonar-key"));

        assertTrue(outcome.notes().stream()
                .anyMatch(n -> n.contains("could not be fetched")));
    }

    @Test
    void shouldIncludeTheDiffInThePrompt() {
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        agent().run(request("sonar-key"));

        assertTrue(llm.lastRequest().userPrompt().contains("retry logic"));
    }

    @Test
    void shouldRecordGovernanceProvenanceAndWarnWhenAbsent() {
        llm.respondWith(new ReviewVerdict("Fine", List.of()));

        AgentOutcome<ReviewVerdict> outcome = agent().run(request("sonar-key"));

        // No skills registered in this test, so the review is ungoverned and must say so.
        assertTrue(outcome.artifact().governanceSkillVersions().isEmpty());
        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("ungoverned")));
    }

    @Test
    void shouldFailWhenTheMergeRequestDoesNotExist() {
        git.returning(null);
        assertThrows(IllegalStateException.class, () -> agent().run(request("sonar-key")));
    }

    @Test
    void shouldSendTheGovernanceTaxonomyInTheSystemPrompt() {
        llm.respondWith(new ReviewVerdict("Fine", List.of()));
        ReviewerAgent governed = new ReviewerAgent(git, jira, sonar, llm,
                new SkillContextResolver(new OneGovernanceSkill()), new PromptTemplates());

        governed.run(request("sonar-key"));

        assertTrue(llm.lastRequest().systemPrompt().contains("CRITICAL blocks approval"));
        assertTrue(llm.lastRequest().systemPrompt().contains("compliance mandatory"));
    }

    private ReviewerAgent agent() {
        return new ReviewerAgent(git, jira, sonar, llm,
                new SkillContextResolver(new NoSkills()), new PromptTemplates());
    }

    private ReviewerAgent.ReviewRequest request(String sonarKey) {
        return new ReviewerAgent.ReviewRequest("wave-1", "epe/epe-rating-ftth-passive", "42", sonarKey);
    }

    private static class NoSkills implements SkillPort {
        @Override public List<SkillDescriptor> resolveSkills(AgentRole role) { return List.of(); }
        @Override public String loadSkillContent(String name) { return ""; }
    }

    private static class OneGovernanceSkill implements SkillPort {
        @Override public List<SkillDescriptor> resolveSkills(AgentRole role) {
            return role == AgentRole.REVIEWER
                    ? List.of(new SkillDescriptor("review-criteria", "Severity taxonomy", true))
                    : List.of();
        }
        @Override public String loadSkillContent(String name) {
            return "CRITICAL blocks approval. MAJOR blocks approval. MINOR does not.";
        }
    }
}
