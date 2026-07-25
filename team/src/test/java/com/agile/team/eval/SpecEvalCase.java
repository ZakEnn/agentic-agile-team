package com.agile.team.eval;

import java.util.List;

/**
 * One graded example for the Spec agent.
 * <p>
 * SDLC_AGENT_PLAN.md §3.2 argues that a per-role eval set is the step that makes
 * agent iteration compound rather than become guesswork: without graded examples,
 * "improving" a prompt is unmeasurable, and the field's own benchmark numbers are
 * too unreliable to substitute for measurement on your own corpus.
 * <p>
 * Cases are plain JSON in {@code src/test/resources/eval/spec-cases.json} so a
 * product owner can add one without touching Java. Real cases should be harvested
 * from history — a Confluence page plus the specification a human actually wrote
 * from it — which is the part that needs the team's data (see IMPLEMENTATION_LOG).
 *
 * @param name              identifier shown in failures
 * @param intent            the request handed to the agent
 * @param keyword           Confluence search term, may be null
 * @param documentation     the retrieved documentation the agent is grounded in
 * @param minCriteria       fewest acceptance criteria an acceptable spec must have
 * @param mustGroundIn      terms from the documentation the spec is expected to use;
 *                          measures whether the agent actually read its context
 * @param mustNotInvent     terms that do not appear in intent or documentation and
 *                          therefore indicate fabrication if the spec asserts them
 */
public record SpecEvalCase(
        String name,
        String intent,
        String keyword,
        String documentation,
        int minCriteria,
        List<String> mustGroundIn,
        List<String> mustNotInvent
) {
    public SpecEvalCase {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("case name required");
        if (intent == null || intent.isBlank()) throw new IllegalArgumentException("case intent required");
        documentation = documentation == null ? "" : documentation;
        mustGroundIn = mustGroundIn == null ? List.of() : List.copyOf(mustGroundIn);
        mustNotInvent = mustNotInvent == null ? List.of() : List.copyOf(mustNotInvent);
        if (minCriteria < 1) minCriteria = 1;
    }
}
