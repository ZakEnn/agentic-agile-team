---
name: review-criteria
description: >
  Code review severity taxonomy, quality checklist, and governance gate criteria.
  Used by the Reviewer agent to assess merge requests and by the Dev agent to
  understand what standards to target. Changes to this Skill affect the ReviewGate
  governance decision and require explicit approval.
applicable_roles:
  - REVIEWER
  - DEV
governance: true
---

# Code Review Criteria

## Severity Taxonomy

When reviewing code, classify each finding into one of these severity levels:

| Severity | Meaning | Blocks Approval? |
|----------|---------|------------------|
| **CRITICAL** | Security vulnerability, data loss risk, or production-breaking bug | ✅ Yes |
| **MAJOR** | Significant logic error, missing error handling, or performance regression | ✅ Yes |
| **MINOR** | Code style violation, naming inconsistency, or missing documentation | ❌ No |
| **SUGGESTION** | Improvement idea, refactoring opportunity, or alternative approach | ❌ No |

## Approval Decision Rules

- **APPROVED**: Zero CRITICAL or MAJOR findings. Minor/Suggestion findings may be noted for future improvement.
- **CHANGES_REQUESTED**: One or more CRITICAL or MAJOR findings present. All must be resolved before re-review.

## Quality Checklist

For each merge request, verify:

### Architecture & Design
- [ ] Changes respect hexagonal architecture boundaries (domain has no infrastructure imports)
- [ ] New domain logic has no Spring framework dependencies
- [ ] Value objects are immutable; entities protect their invariants
- [ ] No business logic leaked into application/infrastructure layers

### Code Quality
- [ ] Methods are ≤ 30 lines; classes are ≤ 300 lines (soft limits, justify exceptions)
- [ ] No raw `null` returns — use `Optional` for queries, throw for invariant violations
- [ ] Exception handling is specific (no `catch (Exception e)` in business code)
- [ ] Thread safety considered for any shared mutable state

### Testing
- [ ] New domain logic has unit tests covering happy path + at least 2 edge cases
- [ ] Test names follow pattern: `should_[expectedBehavior]_when_[condition]`
- [ ] No test logic depends on execution order or external state

### Security
- [ ] No secrets, API keys, or credentials in code (use environment variables)
- [ ] Input validation present at system boundaries (REST controllers, event handlers)
- [ ] SQL injection prevention: parameterized queries only

## SonarQube Quality Gate Alignment

This Skill's criteria align with the project's SonarQube quality gate:
- Coverage ≥ 80% on new code
- No new CRITICAL or BLOCKER issues
- Duplicated lines ≤ 3% on new code
- Maintainability rating A on new code

The `ReviewGate.canComplete()` domain method enforces that BOTH this Skill's
disposition AND SonarQube's automated gate must pass before a Wave can complete.
