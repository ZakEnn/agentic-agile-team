package com.agile.team.eval;

import java.util.List;

/**
 * The result of grading one spec against one case.
 *
 * @param caseName    which case
 * @param passed      no hard failures
 * @param failures    blocking problems: too few criteria, missing grounding, fabrication
 * @param warnings    quality signals that do not block
 * @param criteria    how many acceptance criteria the spec produced
 * @param testable    how many of those read as checkable
 */
public record SpecEvalScore(
        String caseName,
        boolean passed,
        List<String> failures,
        List<String> warnings,
        int criteria,
        int testable
) {
    public SpecEvalScore {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(passed ? "PASS " : "FAIL ").append(caseName)
          .append(" (criteria=").append(criteria)
          .append(", testable=").append(testable).append(")");
        failures.forEach(f -> sb.append("\n    FAILURE: ").append(f));
        warnings.forEach(w -> sb.append("\n    warning: ").append(w));
        return sb.toString();
    }
}
