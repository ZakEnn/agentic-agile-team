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
