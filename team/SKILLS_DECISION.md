# Agent Skills — Decision Table (Phase 1)

## Evaluation Criteria
| # | Criterion | Question |
|---|-----------|----------|
| C1 | Independence from code | Does the knowledge change independently of code? |
| C2 | Cross-agent reuse | Is it reused across multiple contexts or agents? |
| C3 | Progressive disclosure benefit | Does it benefit from lazy-loading (large content)? |
| C4 | Governance / auditability | Does it need first-class versioned governance? |

## Decision Table

| Candidate Skill | Agent(s) | C1 | C2 | C3 | C4 | Verdict | Justification |
|----------------|----------|----|----|----|----|---------|---------------|
| `review-criteria` | REVIEWER, DEV | ✅ | ✅ | ✅ | ✅ | **ADOPT** | Review severity taxonomy, coding standards checklist, and quality gate criteria evolve with team maturity, are large enough to warrant lazy-loading, are consumed by both Reviewer (to judge) and Dev (to target), and directly impact the governance gate — must be auditable. |
| `specification-template` | PO, QA | ✅ | ✅ | ✅ | ❌ | **ADOPT** | The structured template for transforming Confluence content into specs (with examples, acceptance criteria patterns) changes as the team refines its Definition of Ready; QA uses the same template to validate completeness; content is substantial enough for progressive disclosure. |
| `git-conventions` | DEV, REVIEWER | ✅ | ✅ | ⚠️ | ❌ | **ADOPT** | Branch naming, commit message format, MR description template — shared between Dev (to follow) and Reviewer (to enforce). Changes independently of business logic. Moderate size but clear reuse value. |
| PO system prompt | PO | ❌ | ❌ | ❌ | ❌ | **REJECT** | 3 lines, static, single-agent. A constant is more honest engineering. |
| DEV system prompt | DEV | ❌ | ❌ | ❌ | ❌ | **REJECT** | Same as above — trivial, tightly coupled to agent identity. |
| REVIEWER system prompt | REVIEWER | ❌ | ❌ | ❌ | ❌ | **REJECT** | Same — the system prompt establishes identity, not procedural knowledge. |
| QA system prompt | QA | ❌ | ❌ | ❌ | ❌ | **REJECT** | Same reasoning. |
| QA test strategy | QA | ⚠️ | ❌ | ❌ | ❌ | **REJECT** | Currently trivial (3-line prompt). No evidence of complexity warranting externalization. Revisit when test strategy grows. |
| Definition of Ready | PO, DEV | ✅ | ✅ | ❌ | ❌ | **REJECT** | Small checklist (5-10 items). Better as a constant or embedded in `specification-template` Skill. Doesn't warrant its own Skill machinery. |

## Summary
- **3 Skills adopted**: `review-criteria`, `specification-template`, `git-conventions`
- **6 candidates rejected**: system prompts (too small/static), QA test strategy (premature), Definition of Ready (absorbed into `specification-template`)

## Versioning Strategy (Phase 2)
**Git-versioned in the same repository** (`skills/` directory at project root).

Rationale:
- System is young; a separate mutable registry adds operational complexity without proportional benefit.
- Git provides native audit trail (who changed what, when, with what approval via PR).
- Skills reload at application startup (or via a `/actuator/skills/reload` endpoint for hot-reload without redeploy).
- Governance-sensitive Skills (e.g., `review-criteria`) require PR approval from a designated reviewer before merge — enforced via CODEOWNERS on `skills/review-criteria/`.
