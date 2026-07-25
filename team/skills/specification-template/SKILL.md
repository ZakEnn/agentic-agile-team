---
name: specification-template
description: >
  Template and guidelines for transforming Confluence page content into structured
  specifications with acceptance criteria. Used by PO to produce specs and by QA
  to validate completeness against the expected format.
applicable_roles:
  - PO
  - QA
governance: false
---

# Specification Template

## Purpose

When transforming raw Confluence content into an actionable specification, follow
this structured template to ensure completeness and testability.

## Output Format

Every specification MUST contain the following sections:

```markdown
## Title
[Concise, action-oriented title — e.g., "Add pagination to project list endpoint"]

## Context
[1-2 sentences: WHY this change is needed, what user/business problem it solves]

## Scope
[Explicit boundaries — what IS included and what is NOT]

## Functional Requirements
- FR-1: [Requirement in "The system shall..." format]
- FR-2: ...

## Acceptance Criteria
- AC-1: GIVEN [precondition] WHEN [action] THEN [expected outcome]
- AC-2: ...
- AC-3: ...

## Technical Constraints
[Any non-functional requirements: performance targets, compatibility, dependencies]

## Out of Scope
[Explicitly list related items that are NOT part of this specification]
```

## Acceptance Criteria Quality Rules

Each acceptance criterion MUST be:
1. **Testable** — a QA agent can unambiguously determine pass/fail
2. **Independent** — does not depend on other criteria's execution order
3. **Atomic** — tests exactly one behavior (no "and" combining two checks)
4. **Written in Given/When/Then format** — no exceptions

Minimum: **3 acceptance criteria** per specification.
Maximum: **10** — if more are needed, split the specification.

## Specification Sizing

A well-sized specification should be implementable within a single Wave iteration.
Indicators that a spec is too large:
- More than 10 acceptance criteria
- Touches more than 3 bounded contexts
- Requires more than 5 files changed
- Cannot be described in ≤ 200 words of scope

If any indicator triggers, recommend splitting into multiple specifications.

## Source Traceability

Every specification MUST reference its source Confluence page ID so that:
- Changes to the source can trigger re-evaluation
- QA can trace acceptance criteria back to business requirements
- Audit trail is maintained from requirement → spec → implementation → test
