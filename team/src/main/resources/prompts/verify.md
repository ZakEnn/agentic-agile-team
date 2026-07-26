Verify the implementation against its acceptance criteria.

## Acceptance criteria
${acceptanceCriteria}

## What was implemented
${implementationSummary}

Files changed:
${filesChanged}

## Test run
Exit code: ${testExitCode}

```
${testOutput}
```

## Your task

For **each** acceptance criterion above, return a `CriterionResult`:

- `criterion` — the criterion, copied verbatim.
- `verified` — true only if a test in the run above actually demonstrates it.
- `evidence` — name the specific test that demonstrates it, or state precisely what
  is missing.

Also return:

- `failingTests` — names of tests that failed, if any.
- `coverageNote` — anything notable about coverage of the changed files.
- `summary` — two or three sentences on what was verified.

Rules:

1. **A passing suite is not the same as a verified criterion.** If the tests are
   green but nothing exercises a criterion, that criterion is **not** verified. Say
   so — that gap is the single most useful thing you can report.
2. Do not infer behaviour from the implementation summary. Only the test run counts
   as evidence.
3. The overall pass/fail is computed from your per-criterion results and the runner's
   exit code. Do not state an overall verdict yourself.
