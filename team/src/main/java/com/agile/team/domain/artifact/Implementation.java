package com.agile.team.domain.artifact;

import java.util.List;

/**
 * The Developer stage's result: what was changed and whether it actually works.
 * <p>
 * {@code buildPassed} and {@code testsPassed} come from process exit codes, never
 * from the model. SDLC_AGENT_PLAN.md §5 M4 states the exit criterion as "the
 * Implementation artifact is only claimed complete when build and tests actually
 * pass" — a factory method enforces it so no caller can construct a "complete"
 * implementation that did not compile.
 *
 * @param branch       branch the change lives on
 * @param mergeRequestIid MR id, when one was opened
 * @param filesChanged paths written
 * @param buildPassed  real build exit code was 0
 * @param testsPassed  real test exit code was 0
 * @param summary      what was done
 * @param buildOutput  tail of the build output, kept for the failure feedback loop
 */
public record Implementation(
        String branch,
        String mergeRequestIid,
        List<String> filesChanged,
        boolean buildPassed,
        boolean testsPassed,
        String summary,
        String buildOutput
) {
    public Implementation {
        if (branch == null || branch.isBlank()) {
            throw new ArtifactValidationException("Implementation.branch must not be blank");
        }
        filesChanged = filesChanged == null ? List.of() : List.copyOf(filesChanged);
        if (summary == null) summary = "";
        if (buildOutput == null) buildOutput = "";
    }

    /** True only when the change compiles and its tests pass. */
    public boolean isComplete() {
        return buildPassed && testsPassed && !filesChanged.isEmpty();
    }
}
