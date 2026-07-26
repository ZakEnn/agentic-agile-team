package com.agile.team.infrastructure.adapter.jira;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance-criteria extraction from a free-text Jira description.
 * <p>
 * Heuristic by necessity — Jira has no standard field for this. What matters is the
 * failure behaviour: no match returns an <em>empty</em> list, not a placeholder.
 */
class JiraAcceptanceCriteriaTest {

    @Test
    void shouldExtractBulletedCriteria() {
        List<String> criteria = JiraRestClient.extractAcceptanceCriteria("""
                Some preamble.

                Acceptance Criteria:
                - Given a timeout, when polling, then it retries
                - Given three failures, then an alert is raised
                """);

        assertEquals(2, criteria.size());
        assertEquals("Given a timeout, when polling, then it retries", criteria.get(0));
    }

    @Test
    void shouldExtractGivenWhenThenWithoutBullets() {
        List<String> criteria = JiraRestClient.extractAcceptanceCriteria("""
                Acceptance criteria
                Given a valid file
                When it is uploaded
                Then it is archived
                """);

        assertEquals(3, criteria.size());
    }

    @Test
    void shouldExtractNumberedCriteria() {
        List<String> criteria = JiraRestClient.extractAcceptanceCriteria("""
                Acceptance Criteria:
                1. The poller retries three times
                2. An alert is raised on exhaustion
                """);

        assertEquals(2, criteria.size());
    }

    @Test
    void shouldStopAtTheNextSection() {
        List<String> criteria = JiraRestClient.extractAcceptanceCriteria("""
                Acceptance Criteria:
                - First criterion
                Technical details
                - Not a criterion
                """);

        assertEquals(List.of("First criterion"), criteria);
    }

    @Test
    void shouldIgnoreTextBeforeTheCriteriaHeading() {
        List<String> criteria = JiraRestClient.extractAcceptanceCriteria("""
                - This bullet is in the preamble
                Acceptance Criteria:
                - This one counts
                """);

        assertEquals(List.of("This one counts"), criteria);
    }

    @Test
    void shouldReturnEmptyRatherThanAPlaceholderWhenNoneFound() {
        // The ported original returned a single element reading "No explicit
        // acceptance criteria found", which then flowed downstream as though it were
        // a real requirement. Empty means empty.
        assertTrue(JiraRestClient.extractAcceptanceCriteria("Just a description.").isEmpty());
        assertTrue(JiraRestClient.extractAcceptanceCriteria(null).isEmpty());
        assertTrue(JiraRestClient.extractAcceptanceCriteria("").isEmpty());
    }

    @Test
    void shouldNotFetchWhenUnconfigured() {
        JiraRestClient client = new JiraRestClient(
                new JiraRestClient.JiraProperties("https://jira.invalid", "", ""));
        assertTrue(client.fetchIssue("SCA-1").isEmpty());
    }
}
