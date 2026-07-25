package com.agile.team.application.agent;

import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import com.agile.team.support.ScriptedLlmGateway;
import com.agile.team.support.StubConfluencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecAgentTest {

    private ScriptedLlmGateway llm;
    private StubConfluencePort confluence;
    private SpecAgent agent;

    private static final SpecDraft VALID_DRAFT = new SpecDraft(
            "Add retry to SFTP poller",
            "Retry transient failures with exponential backoff.",
            List.of("Given a transient failure, when polling, then retry up to 3 times"),
            List.of("Changing the schedule"));

    @BeforeEach
    void setUp() {
        llm = new ScriptedLlmGateway();
        confluence = new StubConfluencePort();
        // No skills registered: the PO role has no governance skill, which is the
        // real configuration — review-criteria applies to REVIEWER and DEV.
        agent = new SpecAgent(confluence, llm, new SkillContextResolver(new NoSkills()));
    }

    @Test
    void shouldProduceAValidatedSpecificationFromConfluenceContext() {
        confluence.returning("The SFTP poller currently fails permanently on timeout.");
        llm.respondWith(VALID_DRAFT);

        AgentOutcome<SpecDraft> outcome = agent.run(request("SFTP retry"));

        assertEquals(VALID_DRAFT, outcome.artifact());
        assertEquals(150, outcome.usage().total());
        assertTrue(confluence.wasSearched());
        assertEquals("EPE", confluence.searchedSpaces().get(0));
    }

    @Test
    void shouldPassRetrievedDocumentationToTheModel() {
        // Retrieval must be deterministic and the result actually handed to the
        // model — not left to the model to fetch via a tool and hope.
        confluence.returning("POLLER-DOC-MARKER");
        llm.respondWith(VALID_DRAFT);

        agent.run(request("SFTP retry"));

        LlmGateway.LlmRequest sent = llm.lastRequest();
        assertTrue(sent.userPrompt().contains("POLLER-DOC-MARKER"),
                "retrieved documentation should appear in the prompt");
        assertTrue(sent.userPrompt().contains("Supporting documentation"));
    }

    @Test
    void shouldSkipRetrievalWhenNoKeywordIsSupplied() {
        llm.respondWith(VALID_DRAFT);

        AgentOutcome<SpecDraft> outcome = agent.run(request(null));

        assertFalse(confluence.wasSearched());
        assertTrue(outcome.notes().stream()
                .anyMatch(n -> n.contains("drafting from the intent alone")));
    }

    @Test
    void shouldDegradeGracefullyAndFlagWhenRetrievalFails() {
        // A Confluence outage should not fail the stage, but the human at the
        // approval gate must be able to see that the spec was written blind.
        confluence.failingWith(new RuntimeException("connection refused"));
        llm.respondWith(VALID_DRAFT);

        AgentOutcome<SpecDraft> outcome = agent.run(request("SFTP retry"));

        assertEquals(VALID_DRAFT, outcome.artifact());
        assertTrue(outcome.notes().stream()
                        .anyMatch(n -> n.contains("Confluence retrieval failed")
                                && n.contains("connection refused")),
                "the failure must be visible in the audit trail, not swallowed");
    }

    @Test
    void shouldNoteWhenNoDocumentationMatched() {
        confluence.returningNothing();
        llm.respondWith(VALID_DRAFT);

        AgentOutcome<SpecDraft> outcome = agent.run(request("nonexistent"));

        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("No Confluence pages matched")));
    }

    @Test
    void shouldPropagateValidationFailureFromTheModel() {
        // The gateway rejects an unusable artifact; the agent must not paper over it.
        llm.failWith(new com.agile.team.domain.artifact.ArtifactValidationException(
                "SpecDraft.acceptanceCriteria must contain at least one criterion"));

        assertThrows(com.agile.team.domain.artifact.ArtifactValidationException.class,
                () -> agent.run(request("SFTP retry")));
    }

    @Test
    void shouldRequestOutputInTheWaveLanguage() {
        llm.respondWith(VALID_DRAFT);

        agent.run(new SpecAgent.SpecAgentRequest("wave-1", "Do the thing", null,
                new WaveContext("EPE", null, null, "French")));

        assertTrue(llm.lastRequest().userPrompt().contains("French"));
    }

    @Test
    void shouldRejectBlankIntent() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpecAgent.SpecAgentRequest("wave-1", "  ", null, null));
    }

    private SpecAgent.SpecAgentRequest request(String keyword) {
        return new SpecAgent.SpecAgentRequest(
                "wave-1",
                "Make the SFTP poller resilient to transient failures",
                keyword,
                new WaveContext("EPE", "epe-rating-ftth-passive", "SCA", "English"));
    }

    /** Skill registry with nothing in it. */
    private static class NoSkills implements SkillPort {
        @Override
        public List<SkillDescriptor> resolveSkills(com.agile.team.domain.agent.AgentRole role) {
            return List.of();
        }

        @Override
        public String loadSkillContent(String skillName) {
            return "";
        }
    }
}
