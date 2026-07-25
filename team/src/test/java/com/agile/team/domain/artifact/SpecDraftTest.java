package com.agile.team.domain.artifact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The validation that the original PO handler asked for in its prompt and then
 * never enforced. These are the rules that make a spec usable downstream.
 */
class SpecDraftTest {

    @Test
    void shouldAcceptAWellFormedDraft() {
        SpecDraft draft = new SpecDraft(
                "Add retry to the SFTP poller",
                "The poller should retry transient failures with exponential backoff.",
                List.of("Given a transient failure, when polling, then it retries up to 3 times"),
                List.of("Changing the polling schedule"));

        assertEquals("Add retry to the SFTP poller", draft.summary());
        assertEquals(1, draft.acceptanceCriteria().size());
        assertEquals(1, draft.outOfScope().size());
    }

    @Test
    void shouldRejectDraftWithNoAcceptanceCriteria() {
        ArtifactValidationException thrown = assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft("Title", "Description", List.of()));
        assertTrue(thrown.getMessage().contains("at least one criterion"));
    }

    @Test
    void shouldRejectDraftWithNullAcceptanceCriteria() {
        assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft("Title", "Description", null));
    }

    @Test
    void shouldRejectDraftWhoseCriteriaAreAllBlank() {
        // A model returning ["", "  "] is well-formed JSON and completely useless.
        ArtifactValidationException thrown = assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft("Title", "Description", Arrays.asList("", "   ")));
        assertTrue(thrown.getMessage().contains("only blank entries"));
    }

    @Test
    void shouldDropBlankCriteriaButKeepTheRest() {
        SpecDraft draft = new SpecDraft("Title", "Description",
                Arrays.asList("Real criterion", "  ", ""));
        assertEquals(List.of("Real criterion"), draft.acceptanceCriteria());
    }

    @Test
    void shouldTrimCriteria() {
        SpecDraft draft = new SpecDraft("Title", "Description", List.of("  padded  "));
        assertEquals("padded", draft.acceptanceCriteria().get(0));
    }

    @Test
    void shouldRejectBlankSummaryOrDescription() {
        assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft("  ", "Description", List.of("AC")));
        assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft("Title", "", List.of("AC")));
    }

    @Test
    void shouldRejectSummaryLongerThanTheDatabaseColumn() {
        // specifications.title is VARCHAR(500). Catching this here turns a runtime
        // truncation/insert failure into a validation failure the agent can retry.
        String tooLong = "x".repeat(SpecDraft.MAX_SUMMARY_LENGTH + 1);
        ArtifactValidationException thrown = assertThrows(ArtifactValidationException.class,
                () -> new SpecDraft(tooLong, "Description", List.of("AC")));
        assertTrue(thrown.getMessage().contains("at most"));
    }

    @Test
    void shouldDefaultOutOfScopeToEmptyRatherThanNull() {
        SpecDraft draft = new SpecDraft("Title", "Description", List.of("AC"), null);
        assertNotNull(draft.outOfScope());
        assertTrue(draft.outOfScope().isEmpty());
    }
}
