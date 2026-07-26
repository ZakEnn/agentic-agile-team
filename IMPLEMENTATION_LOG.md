# Implementation Log — SDLC_AGENT_PLAN.md

Running log of the autonomous implementation of `SDLC_AGENT_PLAN.md`.
Branch: `sdlc-agent-implementation` (branched from `master` at `568facf`).

Engineering decisions are recorded separately in [`DECISIONS.md`](DECISIONS.md).

---

## M0 — Stop the bleeding

**Status: COMPLETE** · build green · commit `chore(m0)`

### What I built

1. **Secret remediation.** Scanned every working-tree file, not just source. Found
   **8 live credentials**, five more than the plan's analysis documented — because
   that analysis scanned source and missed `docker/*/.env` **and**
   `docker/*/.env.example`:

   | Credential | Location |
   |---|---|
   | Anthropic API key | `team/src/main/resources/application.yaml:37` |
   | Anthropic API key (different key) | `reviewer-agent/src/main/resources/application.yaml:6` |
   | Jira API token | `reviewer-agent/src/main/resources/application.yaml:19` |
   | Confluence API token | `team/docker/mcp-inspector/.env` + `.env.example` |
   | Jira API token | `team/docker/mcp-inspector/.env` + `.env.example` |
   | GitLab PAT (`glpat-…`) | `team/docker/mcp-inspector/.env` + `.env.example` |
   | SonarQube token (`squ_…`) | `team/docker/mcp-inspector/.env` + `.env.example` |
   | Confluence personal token | `team/docker/mcp-atlassian-inspector/.env` |

   All replaced with `${VAR}` references carrying **no default**, so a missing
   variable fails startup rather than silently using a leaked key (D-002).
   DB credentials `root/root` likewise externalised.

2. **History purge.** `reviewer-agent/.git` contained a live Anthropic key in its
   single commit `e7f41e3` (confirmed by `git log -S`). The directory was backed up
   outside the repo and then deleted, so the key never enters the new history.

3. **Repository initialised.** `git init` at the workspace root (it was not a git
   repo at all — every experiment was one `rm -rf` from gone). The **first commit
   is already scrubbed**: no secret exists in any commit of this repository.
   Branched to `sdlc-agent-implementation`.

4. **Defence in depth.** `.gitignore` excludes `.env` (keeping `.env.example`);
   `.githooks/pre-commit` blocks credential-shaped literals and any `.env`;
   a `secret-scan` CI job mirrors the hook so a bypassed local hook is still caught.

5. **CI.** `.github/workflows/ci.yml` — `mvn -B verify` on JDK 21 plus the secret
   scan. Integration-tagged tests run as a separate step.

6. **Made the smoke test runnable on a clean checkout.** `TeamApplicationTests`
   previously required a developer-run MySQL on `localhost:3306`, so CI could never
   be green. It now runs on a `test` profile backed by in-memory H2.

### Decisions made and why

- **D-002** — env vars with *no defaults*. A default is worse than nothing: it turns
  "credential missing" into "silently running on a leaked credential".
- **D-005** — three-tier test strategy. Tier 1 unit, tier 2 H2 slices, tier 3
  Testcontainers only where MySQL semantics genuinely matter.
- Kept `master` at the scrubbed baseline; all implementation work is on the branch,
  so everything is reversible.

### Two real bugs found and fixed along the way

- **`FlywayConfiguration` ignored `spring.flyway.enabled`.** It declared
  `@Bean(initMethod = "migrate")` unconditionally, so the property was a lie and any
  Flyway-less context was impossible. Now `@ConditionalOnProperty`.
- **`JpaConfiguration.flywayDependencyPostProcessor` hard-required a `flyway` bean**,
  failing any context without one. Now guarded on `containsBeanDefinition`.

Useful side effect: the MySQL migration chain runs **cleanly on H2 in MySQL mode**,
so tests execute the real migrations rather than a Hibernate-generated schema.
Test config was changed to `ddl-auto: none` + Flyway enabled to take advantage.

- Removed the explicit `MariaDBDialect` from `application.yaml`. Hibernate warned it
  was unnecessary against MySQL, and it leaked into H2 test contexts. Auto-detection
  is correct for both.

### What I verified

- `mvn -q test` — **green** (exit 0). Full Spring context starts on H2; all 3 Flyway
  migrations apply; 3 skill descriptors load; 7 pre-existing test classes pass.
- Staged-content scan for all 8 known secret literals across the entire repository:
  **clean**.
- `git ls-files` contains no `.env` file.

### What is still open

- **BLOCKED (external action required): the 8 credentials must be revoked at their
  issuers.** Removing them from a repository does not un-leak them. I cannot revoke
  them — that needs console access to Anthropic, Orange GitLab, SonarQube, Jira and
  Confluence. Originals are backed up outside the repo (scratchpad
  `SECRETS-BACKUP/`) purely so they can be identified for revocation.
  The GitLab PAT and SonarQube token are the highest risk: they are write-capable
  against real corporate infrastructure.
- Tier-3 Testcontainers tests not yet written (M3 needs them for `SKIP LOCKED`).
  `mysql:8.4` image is now pulled locally, so the tier is viable.

---

## M1 — One honest vertical slice: intent → approved spec

**Status: COMPLETE** · 94 tests green (was 34 at baseline) · commit `feat(m1)`

### What I built

**The `LlmGateway` port (D-003).** The load-bearing decision of the whole build.
Agents depend on a typed port — `generate(LlmRequest, Class<T>) → LlmResult<T>` —
not on Spring AI. Two implementations: `SpringAiLlmGateway` (real, with token
accounting and Micrometer metrics) and `ScriptedLlmGateway` (test double). This is
why every agent, gate and orchestration path below is testable with no API key, no
network and no cost.

**Validated artifacts.** `SpecDraft` enforces its own invariants in the compact
constructor: at least one non-blank acceptance criterion, non-blank summary and
description, summary within the `VARCHAR(500)` column. The old handler asked for
this in its prompt and enforced none of it.

**The `SpecAgent`.** Retrieval is deterministic (`ConfluencePort`, a real REST
client) and the retrieved text is handed to the model — rather than giving the model
a search tool and hoping. Output binds to `SpecDraft`. Retrieval failure degrades
the spec and records why, instead of failing the stage or silently pretending the
documentation was empty.

**The SPEC_APPROVAL gate, as working machinery.** `GateName`/`GateMode`/
`GateDecision` + `GatePolicy` + REST endpoints (`GET` specs, `POST .../approve`,
`/reject`, `/edit`). Gates default to `REQUIRED`; `AUTO_APPROVE` records
`AUTO:spec-approval` so an automated approval is never mistakable for a human one.
`Wave.startExecution` now **refuses to run without an approved spec**, so the
invariant cannot be routed around by a future handler.

**Conversation is readable.** `GET /api/waves/{id}/conversation`. New `MessageType`
values (`STAGE_STARTED`, `GATE_DECISION`, `AWAITING_APPROVAL`, `BUDGET_EXCEEDED`, …)
so the trail records decisions, not just outputs.

**Observability.** Actuator + Micrometer + Prometheus; `sdlc.llm.tokens`,
`sdlc.llm.calls`, `sdlc.llm.duration`, `sdlc.llm.errors` tagged by role and stage;
`waveId` propagated across the async boundary by a `TaskDecorator`.

**Parameterisation.** `WaveContext` (Confluence space / GitLab project / Jira
project / language) supplied per wave, replacing the hardcoded `"EPE"`,
`"epe-rating-ftth-passive"`, `"SCA"`, `"French"`.

**Eval harness.** `SpecEvalCase` (JSON, editable by a PO) + `SpecEvalScorer` +
`SpecEvalHarness`. The rubric is deterministic — criteria count, testability
markers, grounding in supplied documentation, fabrication check — deliberately
**not** LLM-as-judge: it costs nothing per commit, cannot itself hallucinate, and a
judge sharing the generator's blind spots would agree with exactly the failures
worth catching.

### Decisions made and why

- **D-003 / D-004** — own the port, own the validation. Framework advisors retry;
  domain invariants belong in the domain.
- **D-007** — gates are configuration, never an interactive prompt.
- **D-010 (new)** — the spec stage runs **synchronously** in M1. The durable stage
  machine is M3. Doing it synchronously first keeps M1 deterministic rather than
  layering new agents on the in-JVM `@Async` event bus the plan identified as the
  actual defect. The HTTP call blocks on the model call; that is an accepted
  intermediate state, not the destination.
- **Deleted rather than carried:** all four old handlers, `SpringAiAgentBridge`,
  all four `mcp/*Adapter` classes, `ConfluenceTools`, five event records,
  `CreateSpecificationUseCase`, `CompleteReviewUseCase`. Every one was dead or
  actively misleading (the MCP adapters implemented domain ports by prompting a
  model). Keeping "might be useful" code is how this codebase reached its
  starting state.

### Two framework surprises worth recording

- **Spring Boot 4.1 ships Jackson 3** (`tools.jackson`), not Jackson 2. No
  `com.fasterxml.jackson.databind.ObjectMapper` bean exists to inject, even though
  the Jackson 2 classes are still on the classpath transitively. `WaveMapper` now
  injects `tools.jackson.databind.json.JsonMapper`.
- Acceptance criteria are now stored as **JSON**, not newline-joined. The previous
  `String.join("\n")` / `split("\n")` round trip silently split any multi-line
  Given/When/Then criterion into several — and multi-line criteria are exactly what
  the agent is asked to produce.

### What I verified

- `mvn -o test` — **94 tests, 0 failures**. Includes 9 orchestrator slice tests
  covering: park at gate, auto-approve, human approve, human reject, human edit,
  refuse edit after decision, structured criteria persisted, stage failure fails the
  wave, retrieval gap recorded in the audit trail.
- Migration chain V1.0.0 → V1.4.0 applies cleanly.
- Eval rubric proven to catch: too-few criteria, ignored documentation, fabricated
  technology (the Kafka case), untestable criteria, missing non-goals.

### What is still open

- The spec stage is synchronous; no durability yet (M3).
- Eval cases are authored examples, not harvested history. **The team needs to
  supply ~20 real Confluence-page/specification pairs** for the harness to measure
  anything meaningful about real performance. The machinery is done; the corpus is
  not, and I cannot invent it.
- No live-model eval run — requires an API key (see BLOCKED in the final section).

---

## M2 — Merge `reviewer-agent` in as the Reviewer Agent

**Status: COMPLETE** · 158 tests green · commit `feat(m2)`

The highest-leverage milestone: it converts the best existing asset into the
system's one genuinely differentiated capability, and it delivers standalone value
— a working review bot — even if the rest of the roadmap stalls.

### What I built

**Real toolchain adapters, replacing prompt-and-pray.** `GitLabRestClient`,
`JiraRestClient` and `SonarQubeRestClient` now implement `GitLabPort`, `JiraPort`
and `SonarQubePort` with actual HTTP calls. The three `mcp/*Adapter` classes they
replace implemented the same ports by sending prose to a model with MCP disabled —
they could only fabricate.

**Approval is computed, not asserted.** `Severity` encodes the taxonomy from
`skills/review-criteria/SKILL.md` (`CRITICAL`/`MAJOR` block, `MINOR`/`SUGGESTION`
do not). `ReviewVerdict.approvalStatus()` derives the disposition from classified
findings; the prompt explicitly instructs the model **not** to state one. A verdict
whose summary reads "this is approved and looks great" alongside a CRITICAL finding
returns `CHANGES_REQUESTED` — there is a test for exactly that.

**The gate fails closed.** `CodeQualityScore` gained `QualityGateStatus.UNKNOWN`.
Every SonarQube failure path — unreachable, unconfigured, no analysis (`NONE`),
malformed response, no project key supplied — yields `UNKNOWN`, which does not
pass `ReviewGate`. The hardcoded `passing(80)` is gone.

**Governance provenance.** Each verdict records which governance skills produced it,
and the skill audit trail is now **persisted** (`V1.5.0`, `SkillAuditPort`) instead
of living in an in-memory `ArrayList` that vanished on restart.

**Externalised prompt.** `resources/prompts/review.md`, loaded by `PromptTemplates`.
Substitution is a literal `${name}` replace rather than a templating engine —
deliberately, because prompt values here are **code diffs** full of braces, dollars
and backslashes, and that is exactly the input a templating engine mangles.

**Delivery.** `ReviewCommentFormatter` (decision first, blocking findings before
non-blocking), `PerformReviewUseCase`, and `ReviewController` with a manual trigger
and the GitLab webhook.

### The bug that mattered most

The ported `reviewer-agent` code **had never worked over a webhook**. Its DTOs
declared `objectKind`/`objectAttributes`/`sourceBranch`/`oldPath` with no
`@JsonProperty` and no naming strategy; GitLab sends snake_case. Every field bound
to null, and since Spring Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`, nothing ever
threw — so `payload.objectAttributes().action()` NullPointer-ed on every real
delivery, and the reviewer would have analysed diffs with null branch names.

A defect that produces nulls instead of exceptions is invisible without a test.
`GitLabDtoContractTest` now pins all three payload shapes against recorded fixtures.

### Decisions made and why

- **Explicit `@JsonProperty` over a global `SNAKE_CASE` strategy.** A global strategy
  would silently change how *this service's own* REST API serialises — a wide,
  invisible change to fix a local problem. Annotations keep the contract visible
  where it applies.
- **`RestClient.builder()` rather than an injected `RestClient.Builder`.** Spring
  Boot 4 with the webmvc starter does not auto-configure that bean; this also
  matches what `ConfluenceRestClient` already did.
- **`JiraPort.updateIssueStatus` throws `UnsupportedOperationException`** rather
  than guessing. Jira transitions are workflow-specific, and a wrong transition id
  silently moves an issue to the wrong state — worse than not moving it. Recorded
  as a known limitation rather than faked.
- **Archived `reviewer-agent` to `archive/`** with a README mapping every ported
  piece to its new home. Its stack (Spring Boot 3.5.8, EOL 30 June 2026) cannot
  receive security patches.

### What I verified

- **158 tests, 0 failures.** New coverage: 9 GitLab contract tests against recorded
  payloads, 13 `ReviewVerdict` tests (disposition computation, fail-closed gate),
  11 `ReviewerAgent` tests (real gate attached, degraded Jira, degraded Sonar,
  ungoverned warning), 8 SonarQube mapping tests, 7 Jira extraction tests, 7
  prompt-template tests including the hostile-diff case, 6 formatter tests.
- Migration chain now V1.0.0 → V1.5.0, applies cleanly.

### What is still open

- **No live integration test against a real GitLab/Jira/SonarQube.** The clients are
  verified against recorded payloads, not live endpoints — that needs credentials
  (BLOCKED).
- **Precision tuning is machinery-only.** The plan calls for a 30-MR eval set with
  known dispositions and a tracked false-positive rate. The structure supports it
  (`Finding` is countable by severity), but the corpus needs real reviewed MRs from
  the team.
- `JiraPort.updateIssueStatus` unimplemented (see above).

---

## M3 — Durable orchestration

**Status: COMPLETE** · 179 unit/slice tests + 4 MySQL integration tests green · commit `feat(m3)`

### What I built

**A stage queue in the database** (`V1.6.0`). `stage_run` carries wave, stage,
status, attempt, `max_attempts`, idempotency key, input/output artifact JSON,
`tokens_used`, `available_at` for backoff, and `claimed_by`/`claimed_at`. Claimed
with `SELECT … FOR UPDATE SKIP LOCKED` followed by a conditional
`UPDATE … WHERE status = 'PENDING'`.

Both steps are deliberate: the **conditional UPDATE is the correctness guarantee**
(it succeeds for exactly one caller per row), and **SKIP LOCKED is the contention
optimisation**. Keeping both means the claim stays correct on a database that
ignores the locking hint — and the adapter degrades to a plain select, once and
loudly, if the hint is rejected.

**Instance identity from `CF_INSTANCE_GUID`**, not `CF_INSTANCE_INDEX` — an index
can be reused after a restage, which would make an abandoned claim
indistinguishable from a live one.

**Bounded retry, backoff, dead-letter.** Exponential backoff from 5s, capped at 10
minutes; attempts capped by config; exhaustion lands in `DEAD_LETTER` and fails the
wave. Tokens spent on failed attempts still count toward the budget.

**Crash recovery.** A `RUNNING` row whose owner disappeared is reclaimed after
`stale-claim-seconds` — returned to `PENDING` if attempts remain, dead-lettered if
not. This is the property the in-JVM event bus could not provide at all.

**Budget enforcement.** `waves.tokens_used` is checked before each stage; exceeding
`max-tokens-per-wave` stops the wave with `BUDGET_EXCEEDED` rather than discovering
the cost on an invoice.

**`StageHandler` registry.** One bean per stage; the executor dispatches by enum.
A stage with **no** handler is an explicit stop with a recorded reason — never a
silent skip, because a silent skip would let a wave "complete" without ever being
built or tested. DESIGN/BUILD/VERIFY/RELEASE currently have no handler, and the
tests assert that they can never report success.

**`Orchestrator` no longer executes anything.** It creates the wave and enqueues
SPEC; `StageWorker` claims and runs it. The HTTP caller no longer blocks on a model
call. `StageWorker.pollOnce()` is public so tests drive the real durable path
deterministically instead of sleeping.

### Two real bugs found by tests, not by reading

- **Stale-read after a native UPDATE.** `claimDueStages` claimed the row, then
  re-read it via `findById` and got the *cached* entity — still `PENDING`, still
  `claimed_by = null`. The claim looked successful and returned stale state. Fixed
  with `@Modifying(flushAutomatically = true, clearAutomatically = true)` on every
  bulk query. This is the kind of defect that only surfaces under a test that
  asserts on the value it reads back.
- **Genuinely flaky tests.** Two integration tests failed intermittently across
  repeated runs. Cause: Spring caches one context across test classes, so the H2
  database is shared and leftover `PENDING` rows from another class were claimable.
  Fixed by resetting the queue in `@BeforeEach` in both classes and scoping
  assertions to the wave under test. I ran the suite three consecutive times to
  confirm the fix rather than assuming it.

### Decisions made and why

- **Implemented exactly the approach SDLC_AGENT_PLAN.md §3.3 justified** — DB-backed
  stage machine, no A2A between in-process agents, no Temporal yet. No protocol
  substitution (execution rule 5).
- **Test gates changed from `AUTO_APPROVE` to `REQUIRED`** in the test profile.
  With auto-approval a test asserting "nothing advances before the gate is decided"
  passes for the wrong reason. Tests now drive approval explicitly.
- **Tier-3 tests tagged `integration` and excluded by default** via surefire
  `excludedGroups`, so `mvn verify` stays green with no Docker. CI opts in.

### What I verified

- **179 tests green, three consecutive runs**, no flakiness.
- **4 Testcontainers MySQL tests green** against `mysql:8.4`: the full V1.0.0→V1.6.0
  migration chain applies; `FOR UPDATE SKIP LOCKED` is accepted by real MySQL (no
  fallback); **4 concurrent instances claiming 24 stages produce disjoint sets**;
  duplicate enqueue for the same (wave, stage) is rejected by the unique constraint.
- Restart/crash recovery proven: a stage claimed by a dead instance is reclaimed and
  completes.
- Retry proven: fail → RETRYING → backoff → succeed on attempt 2.
- Dead-letter proven: three failures → DEAD_LETTER → wave FAILED.
- Budget proven: a 600k-token stage against a 500k cap stops the next stage.

### What is still open

- The worker polls on a fixed delay. Fine at this scale; a notification mechanism
  would reduce latency if wave volume grows.
- No outbox table. The plan mentioned one alongside the queue; the stage queue with
  idempotency keys covers the same need here (stage transitions are the only events),
  so a separate outbox would be ceremony without a consumer. Revisit if external
  systems need to subscribe to wave events.
