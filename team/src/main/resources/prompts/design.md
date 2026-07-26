Decide how the following specification should be implemented.

## Specification
${summary}

${description}

### Acceptance criteria
${acceptanceCriteria}

### Out of scope
${outOfScope}

## Repository listing
${repositoryListing}

## Your task

Return a `DesignNote`:

- `approach` — how the change should be made, in a few sentences. Enough for a
  developer to start without guessing, not a line-by-line plan.
- `impactedModules` — the modules or paths this change touches.
- `risks` — what could go wrong, including anything the specification leaves
  ambiguous.
- `testStrategy` — how the acceptance criteria above should be covered by tests.
- `adrRequired` — true if this decision is one a future maintainer would want
  recorded and justified.

Rules:

1. **Only name modules that appear in the repository listing above.** Every name is
   checked against it. A name that is not there fails this stage.
2. If the listing contains no suitable location for part of the change, say so in
   `approach`. Inventing a plausible path is the worst available answer: it survives
   this stage and fails two stages later with no obvious cause.
3. Do not write the implementation. The developer needs a direction, not a diff.
