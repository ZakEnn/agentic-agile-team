package com.agile.team.eval;

import com.agile.team.domain.artifact.SpecDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grades a {@link SpecDraft} against a {@link SpecEvalCase}.
 * <p>
 * The rubric is deliberately made of cheap, deterministic checks rather than an
 * LLM-as-judge. Three reasons: it costs nothing to run on every commit, it cannot
 * itself hallucinate, and a judge model sharing the generator's blind spots would
 * quietly agree with exactly the failures we most need to catch.
 * <p>
 * These checks do not measure whether a spec is <em>good</em>. They measure whether
 * it is <em>usable</em>: enough criteria, criteria that could actually fail a test,
 * grounded in the supplied documentation, and free of asserted detail that appears
 * nowhere in its inputs. That is the floor a downstream agent depends on.
 */
public class SpecEvalScorer {

    /** Words that make a criterion checkable. */
    private static final List<String> TESTABLE_MARKERS = List.of(
            "given", "when", "then", "should", "must", "returns", "rejects",
            "retries", "logs", "fails", "succeeds", "within", "at least", "at most");

    public SpecEvalScore score(SpecEvalCase evalCase, SpecDraft draft) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (draft.acceptanceCriteria().size() < evalCase.minCriteria()) {
            failures.add("expected at least %d acceptance criteria but got %d"
                    .formatted(evalCase.minCriteria(), draft.acceptanceCriteria().size()));
        }

        long testable = draft.acceptanceCriteria().stream().filter(this::looksTestable).count();
        if (testable < draft.acceptanceCriteria().size()) {
            List<String> untestable = draft.acceptanceCriteria().stream()
                    .filter(c -> !looksTestable(c))
                    .toList();
            // A warning rather than a failure: the heuristic is a word list, and a
            // well-formed criterion can legitimately avoid all of these words.
            warnings.add("criteria with no obvious pass/fail condition: " + untestable);
        }

        String haystack = (draft.summary() + " " + draft.description() + " "
                + String.join(" ", draft.acceptanceCriteria())).toLowerCase(Locale.ROOT);

        for (String term : evalCase.mustGroundIn()) {
            if (!haystack.contains(term.toLowerCase(Locale.ROOT))) {
                failures.add("spec does not mention '%s', which appears in the supplied documentation"
                        .formatted(term));
            }
        }

        for (String term : evalCase.mustNotInvent()) {
            if (haystack.contains(term.toLowerCase(Locale.ROOT))) {
                failures.add("spec asserts '%s', which appears in neither the intent nor the documentation"
                        .formatted(term));
            }
        }

        if (draft.outOfScope().isEmpty()) {
            warnings.add("no non-goals stated; scope drift is cheaper to prevent here than to review later");
        }

        return new SpecEvalScore(evalCase.name(), failures.isEmpty(), failures, warnings,
                draft.acceptanceCriteria().size(), (int) testable);
    }

    private boolean looksTestable(String criterion) {
        String lower = criterion.toLowerCase(Locale.ROOT);
        return TESTABLE_MARKERS.stream().anyMatch(lower::contains);
    }
}
