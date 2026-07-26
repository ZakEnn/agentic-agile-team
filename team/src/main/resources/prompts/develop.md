Implement the following specification.

## Specification
${summary}

${description}

### Acceptance criteria
${acceptanceCriteria}

### Out of scope
${outOfScope}

## Design note
${designNote}

## Previous attempt
${previousFailure}

## Your task

Return a `ChangePlan`:

- `rationale` — two or three sentences on the approach you took.
- `changes` — one entry per file, each with a workspace-relative `path` and the
  **complete** `content` of that file.

Rules:

1. **Complete files only.** Every file you return is written to disk verbatim and
   then compiled. Fragments, diffs, and "... unchanged ..." markers produce a file
   that does not build.
2. **Include tests** covering the acceptance criteria you implement. The QA stage
   verifies each criterion against a test; a criterion with no test cannot pass.
3. **Paths are relative to the workspace root** and must not contain `..`.
4. If a previous attempt is shown above, read its build output carefully and fix
   the actual reported error. Repeating the same change will fail the same way.
5. Stay inside the specification. Anything listed as out of scope is not yours to
   change, however tempting.
