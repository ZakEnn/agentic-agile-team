# Engineering Decisions Log

Decisions made while implementing `SDLC_AGENT_PLAN.md`. Each entry records the
choice, the alternatives considered, and why — so a reviewer can disagree with
the reasoning rather than guess at it.

Format: `D-NNN — <decision>` · **Context** · **Decision** · **Why** · **Consequence**

---

## D-001 — Single Maven module, kept at `team/`

**Context.** The plan names `team` as the chassis and `reviewer-agent` as the
donor. Both are standalone Maven projects. A multi-module reactor was possible.

**Decision.** Consolidate into the single existing module at `team/`. Keep the
directory name `team` rather than renaming to something grander.

**Why.** A 2–4 person team gets nothing from a reactor at this size, and renaming
the directory would churn every path in the plan, the analysis, and the IDE config
for zero functional gain. The package root `com.agile.team` already reads as
"the agile team of agents".

**Consequence.** `reviewer-agent/` becomes a donor tree that is archived once its
code is ported (M2), not a live module.

---

## D-002 — Secrets: env vars with no defaults, plus a pre-commit guard

**Context.** M0. Eight live credentials were found in the working trees — three
in source (2x Anthropic, 1x Jira) and five more in `docker/*/.env` **and**
`docker/*/.env.example`, which the original plan's analysis missed because it
scanned source only.

**Decision.** Every secret becomes `${VAR}` with **no default**. `.env` is
gitignored; `.env.example` holds only placeholders. A `.githooks/pre-commit`
hook blocks credential-shaped literals and any `.env` file. `reviewer-agent/.git`
(which had a live key in commit `e7f41e3`) was deleted rather than imported.

**Why.** A default value means a missing env var silently falls back to a leaked
credential — the failure mode is "works, on the wrong key". No default means the
app fails loudly at startup, which is the correct behaviour.

**Consequence.** The apps will not start without env vars set. That is intended.
The credentials still must be **revoked at their issuers** — deleting them from a
repo does not un-leak them. Originals are backed up outside the repo.

---

## D-003 — An `LlmGateway` port, not a direct Spring AI dependency in agents

**Context.** No Anthropic API key is available (and using one would be a paid
call against a third party). Every agent still needs to be testable.

**Decision.** Agents depend on an `LlmGateway` interface that takes a typed
request and returns a typed artifact. Two implementations: `SpringAiLlmGateway`
(real, uses Spring AI structured output) and `ScriptedLlmGateway` (test double
returning canned typed responses).

**Why.** This is the single most important testability decision in the build.
It means all six agents, all validation, all orchestration, and the whole gate
logic are unit-testable with zero network, zero API key, and zero cost. It also
makes the "delegate the DEV agent to an external executor" move from the plan a
matter of swapping an implementation.

**Consequence.** Agent logic is verified; the *Spring AI binding itself* is only
verified by a narrow adapter test. That boundary is stated honestly in the log.

---

## D-004 — Validation is ours, not the framework's

**Context.** The plan cites Spring AI 2.0's `StructuredOutputValidationAdvisor`,
which self-corrects on validation failure.

**Decision.** Use Spring AI for *parsing* (JSON → record), but implement the
*validation rules* ourselves in `domain/artifact/ArtifactValidator` and the
records' compact constructors.

**Why.** Business rules like "a spec must have at least one acceptance criterion"
are domain invariants, not framework concerns. Owning them means they are unit-
testable without a model, and they cannot be silently weakened by a framework
upgrade. The advisor still adds value as a retry mechanism on top.

**Consequence.** Slight duplication of intent with the framework, accepted.

---

## D-005 — Test strategy: unit-first, H2 for slices, Testcontainers only where MySQL semantics matter

**Context.** Flyway migrations are MySQL-specific (`ENGINE=InnoDB`, `CHAR(36)`).
Docker is available but no MySQL image was cached locally.

**Decision.** Three tiers:
1. **Pure unit tests** (no Spring, no DB) for domain, artifacts, validators,
   agents, gate logic, and orchestration decision logic. This is the bulk.
2. **H2 (MySQL mode) + `ddl-auto: create-drop`** for JPA/persistence slices,
   skipping Flyway.
3. **Testcontainers MySQL** only for what genuinely needs real MySQL: the Flyway
   migration chain and the `FOR UPDATE SKIP LOCKED` queue claim.

**Why.** Tier 1 gives fast, deterministic feedback with no infrastructure — CI
stays green on a clean checkout, which is an explicit M0 exit criterion. Tier 3
is where MySQL-specific SQL would otherwise go unverified, so it is worth the cost.

**Consequence.** If the MySQL image cannot be pulled, tier 3 is skipped and that
is logged as unverified rather than silently passing.

---

## D-006 — Orchestration: DB-backed stage machine, exactly as the plan specifies

**Context.** Plan §3.3 justifies a durable stage machine on the existing MySQL
using `SELECT ... FOR UPDATE SKIP LOCKED`, explicitly rejecting A2A between
in-process agents and deferring Temporal.

**Decision.** Implement exactly that. No protocol substitution.

**Why.** The plan's reasoning holds: the actual defect is lost in-flight state on
restart, and a queue table fixes it with zero new infrastructure, which matters
because the target deployment is Cloud Foundry multi-instance with a shared
datasource and no Redis.

**Consequence.** Stage handoffs carry typed artifacts, so swapping the transport
(Temporal, or A2A at an externalized Developer Agent) is a later transport change,
not a redesign — which is the property the plan asked for.

---

## D-007 — Human-in-the-loop gates are configuration, not prompts

**Context.** Execution rule 6: HITL checkpoints must be code-level toggles, not
questions asked during this build.

**Decision.** Each gate is a `GatePolicy` resolved from configuration
(`sdlc.gates.<gate>.mode = REQUIRED | AUTO_APPROVE | DISABLED`). In `REQUIRED`
mode the stage machine parks the wave in `AWAITING_APPROVAL` and waits for a REST
call. `AUTO_APPROVE` records a synthetic approval with `authorizedBy=AUTO:<policy>`
so the audit trail never lies about who approved.

**Why.** Makes supervised autonomy the default and full autonomy a config change,
which is exactly the maturity ladder the plan argues for. Auto-approvals are
distinguishable from human ones in the audit trail, so trust metrics stay honest.

**Consequence.** Tests run with `AUTO_APPROVE` to exercise the full pipeline;
production defaults ship as `REQUIRED`.

---

## D-008 — The Developer Agent delegates through a `CodeExecutor` port

**Context.** Plan §5 M4 recommends buying (Claude Code / OpenHands) over building
a code-writing loop, and flags this as the highest-risk milestone.

**Decision.** Define a `CodeExecutor` port with `execute(WorkspaceRequest)`.
Ship two implementations: `LocalCommandCodeExecutor` (runs a real build/test
command in a working tree and reports genuine exit codes) and a
`ScriptedCodeExecutor` for tests. An `OpenHandsCodeExecutor` / Claude Code
adapter is left as a documented extension point.

**Why.** The honest capability here is "run a build and report the truth about
whether it passed". That is buildable and verifiable today without an API key,
and it is the part the gate actually depends on. Wiring a specific vendor agent
is a credential-gated integration, not an architectural question.

**Consequence.** The pipeline is end-to-end runnable with a real build, but the
"agent writes the code" step is a documented BLOCKED integration point rather
than a pretend implementation.

---

## D-010 — The spec stage runs synchronously in M1; durability arrives in M3

**Context.** M1 needs the spec stage working end to end. The existing handoff
mechanism is an in-JVM `ApplicationEventPublisher` + `@Async` bus, which
SDLC_AGENT_PLAN.md identifies as the actual defect (no durability, no retry, no
multi-instance safety).

**Decision.** Run the spec stage synchronously inside `Orchestrator.startWave`.
Do not build new agents on top of the event bus. Replace it wholesale in M3 with
the DB-backed stage machine.

**Why.** Two bad options were available — keep the broken async bus, or build the
durable machine before a single agent works end to end. Synchronous execution is a
third: it makes M1 deterministic and testable, and it means the durable machine in
M3 is introduced against a working pipeline rather than a hypothetical one.

**Consequence.** The HTTP call blocks for the duration of a model call. Acceptable
for M1, unacceptable as a destination — M3 removes it.

---

## D-011 — Eval rubric is deterministic, not LLM-as-judge

**Context.** M1 requires an eval harness for the Spec agent.

**Decision.** Score with cheap deterministic checks: criteria count, testability
markers, grounding in supplied documentation, fabrication detection.

**Why.** Three reasons an LLM judge loses here: it costs money on every commit so it
would not run on every commit; it can hallucinate its own verdict; and a judge model
sharing the generator's blind spots agrees with precisely the failures most worth
catching. The rubric does not measure whether a spec is *good* — it measures whether
it is *usable*, which is the floor downstream agents depend on.

**Consequence.** Subtle quality regressions will not be caught. An LLM-judge tier
can be added later as a supplement, never as the gate.

---

## D-012 — The Release agent uses no language model

**Context.** M5. Every other SDLC domain got an agent backed by `LlmGateway`.

**Decision.** `ReleaseAgent` calls no model at all. It reads facts (allowed
environment, frozen list, deployment window, currently-running version) and acts.

**Why.** There is no judgment here for a model to add. Whether an environment is
frozen is a lookup, not an opinion. Introducing a model would insert
non-determinism into the single most irreversible step in the pipeline, in exchange
for nothing. The plan's principle is "the LLM judges; it never integrates" — at this
stage there is nothing to judge.

**Consequence.** The roster is "one agent per SDLC domain", not "one model call per
SDLC domain". If deploy-readiness later needs real judgment (interpreting canary
metrics, say), that is the point to add a model — to the judgment, not to the action.

---

## D-013 — Design modules are verified against the repository, and that is on by default

**Context.** M5. The Architect agent names the modules a change will touch.

**Decision.** Every named module is checked against the real repository tree.
Unverifiable means the stage fails. `sdlc.design.require-module-verification=false`
is an explicit opt-out that degrades to a loud `UNVERIFIED` note.

**Why.** An invented module name is the cheapest possible thing to catch and one of
the most expensive to miss: it survives the design stage, becomes an invented file
in the build stage, and surfaces as a compiler error with no obvious cause. Failing
at the moment the claim is made costs one retry; failing later costs a confusing
debugging session.

**Consequence.** DESIGN cannot run against a repository the system cannot read.
That is intended: designing against a repository you cannot see is guessing.

---

## D-009 — `CodeQualityScore` gains an explicit `UNKNOWN` state

**Context.** The original `ReviewerAgentHandler` hardcoded
`CodeQualityScore.passing(80)` "since we don't want to block on unavailable
SonarQube" — turning the governance gate into theatre.

**Decision.** Add `CodeQualityScore.unknown()`. `ReviewGate.canComplete` treats
`UNKNOWN` as **not passed** (fail closed).

**Why.** Missing evidence is not positive evidence. The plan states this as a
first-class principle; encoding it in the value object makes it impossible to
regress by accident in a handler.

**Consequence.** A wave cannot complete while SonarQube is unreachable. That is
the intended behaviour and must be operationally understood.
