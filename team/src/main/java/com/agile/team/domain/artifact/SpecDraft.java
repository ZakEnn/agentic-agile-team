package com.agile.team.domain.artifact;

import java.util.List;

/**
 * The Spec Agent's output artifact: a testable specification.
 * <p>
 * This replaces the original behaviour, where {@code PoAgentHandler} demanded strict
 * JSON in its prompt and then stored the raw model response verbatim as
 * {@code Specification.content} while passing {@code List.of()} for acceptance
 * criteria — discarding exactly the structured criteria the QA agent is supposed to
 * verify later.
 * <p>
 * Validation lives here rather than in a framework advisor (DECISIONS.md D-004):
 * "a specification must be testable" is a domain invariant, and encoding it in the
 * compact constructor makes it impossible to construct an invalid draft anywhere in
 * the system, including from persistence or from a test.
 *
 * @param summary            concise ticket title
 * @param description        what needs to be done
 * @param acceptanceCriteria at least one testable criterion; these become the QA
 *                           agent's checklist, so an empty list makes the whole
 *                           downstream pipeline unverifiable
 * @param outOfScope         explicit non-goals; cheap to produce and materially
 *                           reduces scope drift in the Developer stage
 */
public record SpecDraft(
        String summary,
        String description,
        List<String> acceptanceCriteria,
        List<String> outOfScope
) {

    public static final int MAX_SUMMARY_LENGTH = 500;

    public SpecDraft {
        if (summary == null || summary.isBlank()) {
            throw new ArtifactValidationException("SpecDraft.summary must not be blank");
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new ArtifactValidationException(
                    "SpecDraft.summary must be at most " + MAX_SUMMARY_LENGTH
                            + " characters but was " + summary.length());
        }
        if (description == null || description.isBlank()) {
            throw new ArtifactValidationException("SpecDraft.description must not be blank");
        }
        if (acceptanceCriteria == null || acceptanceCriteria.isEmpty()) {
            throw new ArtifactValidationException(
                    "SpecDraft.acceptanceCriteria must contain at least one criterion — "
                            + "a specification with no criteria cannot be verified by the QA agent");
        }
        List<String> cleanedCriteria = acceptanceCriteria.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .toList();
        if (cleanedCriteria.isEmpty()) {
            throw new ArtifactValidationException(
                    "SpecDraft.acceptanceCriteria contained only blank entries");
        }
        acceptanceCriteria = cleanedCriteria;

        outOfScope = outOfScope == null
                ? List.of()
                : outOfScope.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .toList();
    }

    /** Convenience for callers that do not supply non-goals. */
    public SpecDraft(String summary, String description, List<String> acceptanceCriteria) {
        this(summary, description, acceptanceCriteria, List.of());
    }
}
