# Archive

Projects that have been superseded. Kept for reference, not built, not deployed.

## `reviewer-agent/`

**Status: archived at M2. Its working capability now lives in `team/`.**

`reviewer-agent` was the more mature of the two starting projects: it had the only
real toolchain integrations in the repository — genuine GitLab, Jira and SonarQube
REST clients, a versioned prompt template, and structured output binding — while
`team` had the domain model and the governance concept but implemented its ports by
prompting a language model.

It was archived rather than developed further for a concrete reason: it ran on
**Spring Boot 3.5.8**, which reached end of life on **30 June 2026**, and
**Spring AI 1.1.0**. Spring AI 2.0 (GA 12 June 2026) requires Spring Boot 4.0/4.1.
Porting ~600 lines of working client code forward onto `team`'s supported stack was
substantially cheaper than back-porting `team`'s domain, persistence and governance
onto a dead branch.

### Where each piece went

| Original | Now |
|---|---|
| `GitLabProperties.GitLabClient` (nested in a record) | `team/…/infrastructure/adapter/gitlab/GitLabRestClient.java` |
| GitLab response DTOs | `team/…/infrastructure/adapter/gitlab/GitLabDtos.java` |
| `JiraClient` + acceptance-criteria extraction | `team/…/infrastructure/adapter/jira/JiraRestClient.java` |
| `SonarQubeClient` | `team/…/infrastructure/adapter/sonarqube/SonarQubeRestClient.java` |
| `AiCodeReviewService.REVIEW_PROMPT_TEMPLATE` | `team/src/main/resources/prompts/review.md` |
| `ReviewComment` record | `team/…/domain/artifact/ReviewVerdict.java` + `domain/review/Finding.java` |
| `ReviewOrchestrator.formatReviewAsMarkdown` | `team/…/application/review/ReviewCommentFormatter.java` |
| `CodeReviewController` webhook | `team/…/interfaces/rest/ReviewController.java` |
| `ReviewOrchestrator.performCompleteReview` | `team/…/application/agent/ReviewerAgent.java` + `PerformReviewUseCase` |

### What was fixed in the port, not carried over

- **Snake_case deserialization.** The DTOs declared `sourceBranch`, `targetBranch`,
  `oldPath`, `newPath`, `objectKind`, `objectAttributes` with no `@JsonProperty` and
  no naming strategy, while GitLab returns snake_case. Every one bound to null, and
  because Spring Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`, nothing threw. **The
  webhook endpoint NullPointer-ed on every real GitLab delivery** — it had never
  worked. Now fixed with explicit annotations and pinned by
  `GitLabDtoContractTest` against recorded payloads.
- **Model-asserted disposition.** `overallAssessment` was free text. Approval is now
  computed from classified finding severities.
- **`gatherMergeRequestContext`** routed a deterministic API fetch through the LLM
  for no benefit; dropped in favour of the direct path.
- **Three 0-byte files** under `domain/sonarqube/`, and SonarQube types misfiled in
  `domain/jira/`.
- **`extractAcceptanceCriteria`** returned `["No explicit acceptance criteria found"]`
  when it found none, which then flowed into prompts as though it were a real
  requirement. It now returns an empty list.

### Deleting it

Everything of value is ported and covered by tests. This tree can be deleted
outright; it is retained only so the port can be diffed against its source. Its
original git history was **not** imported — that history contained a live Anthropic
API key (commit `e7f41e3`).
