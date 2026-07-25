---
name: git-conventions
description: >
  Branch naming, commit message format, and merge request conventions.
  Used by Dev to follow standards and by Reviewer to enforce them.
applicable_roles:
  - DEV
  - REVIEWER
governance: false
---

# Git Conventions

## Branch Naming

Pattern: `<type>/<ticket-id>-<short-description>`

| Type | Usage |
|------|-------|
| `feature/` | New functionality |
| `fix/` | Bug fix |
| `refactor/` | Code restructuring without behavior change |
| `chore/` | Build, CI, dependency updates |

Examples:
- `feature/TEAM-42-add-pagination`
- `fix/TEAM-108-null-pointer-on-empty-wave`
- `refactor/TEAM-55-extract-skill-port`

Rules:
- Always branch from `main`
- Use lowercase with hyphens (no underscores, no camelCase)
- Keep description ≤ 5 words

## Commit Message Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Types
`feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`

### Scope
The domain area: `agent`, `wave`, `review`, `specification`, `task`, `infrastructure`

### Rules
- Subject line: imperative mood, ≤ 72 characters, no period at end
- Body: explain WHY (not what — the diff shows what)
- Footer: reference ticket (`Refs: TEAM-42`) or breaking changes

### Examples
```
feat(review): add severity taxonomy to review criteria

The reviewer agent now classifies findings by severity level
(CRITICAL, MAJOR, MINOR, SUGGESTION) to provide structured
feedback that maps to approval decisions.

Refs: TEAM-201
```

## Merge Request Conventions

### Title
Same format as commit subject: `<type>(<scope>): <subject>`

### Description Template
```markdown
## What
[1-2 sentences describing the change]

## Why
[Business/technical motivation]

## How
[Brief technical approach]

## Testing
[How this was validated]

## Checklist
- [ ] Unit tests added/updated
- [ ] No new compiler warnings
- [ ] Documentation updated if needed
```

### Review Readiness
A merge request is ready for review when:
- CI pipeline passes (build + tests green)
- Description is filled out completely
- No WIP/Draft prefix in title
- Assigned to at least one reviewer
