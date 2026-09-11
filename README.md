# Agile Team of Agents

## Project Overview

**agile-team** is a Spring Boot application that orchestrates an autonomous agile team of AI agents. Each agent fulfills a specific Scrum role (PO, DEV, REVIEWER, QA) and collaborates through an event-driven pipeline to deliver software increments called **Waves**.

The system uses **Spring AI** with Claude (Anthropic) as the LLM backbone and **MCP (Model Context Protocol)** clients to interact with external tools (Confluence, GitLab, Jira, SonarQube).

---

## Architecture

### DDD + Hexagonal (Ports & Adapters)

```
src/main/java/com/agile/team/
├── domain/            # Core business logic — zero framework dependencies
│   ├── agent/         # Agent entity, AgentRole enum, AgentRepository interface
│   ├── wave/          # Wave aggregate (lifecycle state machine), WaveStatus, WaveRepository
│   ├── task/          # Task entity with status tracking
│   ├── specification/ # Specification value object (title, content, acceptance criteria)
│   ├── conversation/  # ConversationHistory aggregate, AgentMessage, MessageType
│   ├── review/        # ReviewGate, ReviewDisposition, CodeQualityScore, ApprovalStatus
│   └── port/          # Outbound port interfaces (ConfluencePort, GitLabPort, JiraPort, SonarQubePort, SkillPort)
│
├── application/       # Use cases and orchestration — depends on domain only
│   ├── usecase/       # StartWaveUseCase, CreateSpecificationUseCase, CompleteReviewUseCase, SkillGovernanceUseCase
│   ├── handler/       # Agent handlers (PoAgentHandler, DevAgentHandler, ReviewerAgentHandler, QaAgentHandler)
│   ├── orchestrator/  # Orchestrator — coordinates wave lifecycle and agent handoffs
│   └── event/         # Domain events (AgentTaskAssignedEvent, SpecificationReadyEvent, etc.)
│
├── infrastructure/    # Technical implementations
│   ├── adapter/
│   │   ├── ai/        # SpringAiAgentBridge — LLM integration with skill augmentation
│   │   ├── mcp/       # MCP adapters (ConfluenceAdapter, GitLabAdapter, JiraAdapter, SonarQubeAdapter)
│   │   └── skill/     # SkillRegistryAdapter — filesystem-based skill loading
│   ├── config/        # Spring configurations (Async, JPA, MCP client)
│   └── persistence/   # JPA entities, mappers, repository adapters
│
└── interfaces/        # Entry points
    └── rest/          # WaveController, AgentController, DTOs
```

---

## Agent Roles

| Role | Class | Responsibilities | External Tools |
|------|-------|-----------------|----------------|
| **PO** (Product Owner) | `PoAgentHandler` | Fetches Confluence pages, transforms them into structured specifications with acceptance criteria | Confluence |
| **DEV** (Developer) | `DevAgentHandler` | Creates feature branches, implements changes, submits merge requests | GitLab |
| **REVIEWER** | `ReviewerAgentHandler` | Reviews merge requests, classifies findings by severity, determines approval disposition | GitLab, SonarQube |
| **QA** | `QaAgentHandler` | Validates implementation against acceptance criteria, provides PASSED/FAILED verdict | — |

---

## Wave Lifecycle (State Machine)

A **Wave** is the unit of work — analogous to a sprint iteration.

```
┌──────────┐    startExecution()    ┌─────────────┐    moveToReview()    ┌───────────┐    complete()    ┌───────────┐
│ PLANNING │ ──────────────────────▶│ IN_PROGRESS │ ──────────────────▶ │ IN_REVIEW │ ──────────────▶ │ COMPLETED │
└──────────┘                        └─────────────┘                      └───────────┘                  └───────────┘
                                          │                                    │
                                          │ fail()                             │ fail()
                                          ▼                                    ▼
                                    ┌──────────┐                         ┌──────────┐
                                    │  FAILED  │                         │  FAILED  │
                                    └──────────┘                         └──────────┘
```

**Invariants:**
- Cannot start execution without at least one specification
- Cannot complete without passing the **ReviewGate** (reviewer APPROVED + SonarQube quality gate passed)
- All state transitions are recorded with an `authorizedBy` audit trail

---

## Agent Interaction Flow (Event-Driven)

The agents communicate asynchronously through Spring Application Events. Each handler is `@Async` to enable non-blocking processing.

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                              WAVE EXECUTION PIPELINE                                │
├────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                    │
│  ┌─────────────┐  AgentTaskAssignedEvent  ┌────────────────┐                      │
│  │ Orchestrator│ ────────────────────────▶ │  PO Agent      │                      │
│  │ startWave() │                           │  (PoHandler)   │                      │
│  └─────────────┘                           └───────┬────────┘                      │
│                                                    │                               │
│                                  SpecificationReadyEvent                            │
│                                                    │                               │
│                                                    ▼                               │
│                                            ┌───────────────┐                       │
│                                            │  DEV Agent    │                       │
│                                            │  (DevHandler) │                       │
│                                            └───────┬───────┘                       │
│                                                    │                               │
│                                ImplementationCompleteEvent                          │
│                                                    │                               │
│                                                    ▼                               │
│                                            ┌────────────────┐                      │
│                                            │ REVIEWER Agent │                      │
│                                            │(ReviewHandler) │                      │
│                                            └───────┬────────┘                      │
│                                                    │                               │
│                                   ReviewCompleteEvent                               │
│                                    (+ ReviewGate check)                             │
│                                                    │                               │
│                                          ┌─────────┴─────────┐                     │
│                                          │                   │                     │
│                                    Gate PASSES          Gate FAILS                  │
│                                          │                   │                     │
│                                          ▼                   ▼                     │
│                                  ┌──────────────┐    QaCompleteEvent(failed)        │
│                                  │  QA Agent    │           │                      │
│                                  │ (QaHandler)  │           │                      │
│                                  └──────┬───────┘           │                      │
│                                         │                   │                      │
│                                  QaCompleteEvent            │                      │
│                                   (passed/failed)           │                      │
│                                         │                   │                      │
│                                         ▼                   ▼                      │
│                                  ┌─────────────┐    ┌─────────────┐                │
│                                  │ Orchestrator │    │ Orchestrator │               │
│                                  │  (complete)  │    │   (fail)    │               │
│                                  └─────────────┘    └─────────────┘                │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### Event Sequence Detail

| # | Event | Producer | Consumer | Action |
|---|-------|----------|----------|--------|
| 1 | `AgentTaskAssignedEvent` | Orchestrator | PoAgentHandler | PO fetches Confluence page, creates specification using AI, publishes SpecificationReadyEvent |
| 2 | `SpecificationReadyEvent` | PoAgentHandler | DevAgentHandler | DEV creates feature branch, implements changes, creates MR, publishes ImplementationCompleteEvent |
| 3 | `ImplementationCompleteEvent` | DevAgentHandler | ReviewerAgentHandler | REVIEWER validates governance skills, reviews MR via AI, gets SonarQube quality score, publishes ReviewCompleteEvent |
| 4 | `ReviewCompleteEvent` | ReviewerAgentHandler | QaAgentHandler + Orchestrator | QA checks governance gate first — if passed, performs acceptance testing; Orchestrator transitions wave to IN_REVIEW |
| 5 | `QaCompleteEvent` | QaAgentHandler | Orchestrator | If QA passed and wave is IN_REVIEW, wave can be completed; if failed, wave is marked FAILED |

---

## Governance Gate (ReviewGate)

The `ReviewGate` is a domain policy that **blocks wave completion** unless both conditions are met:

1. **Reviewer disposition** = `APPROVED` (no CRITICAL or MAJOR findings)
2. **SonarQube quality gate** = `PASSED` (coverage ≥ 80%, no blockers, etc.)

```java
ReviewGate.canComplete(reviewDisposition, qualityScore)
// Both must be true — no override path exists
```

The QA agent checks the gate before running — if it fails, the pipeline stops immediately with a `GOVERNANCE_CHECK` message recorded in conversation history.

---

## Skills System

Skills are filesystem-based units of procedural knowledge loaded at runtime from the `skills/` directory.

### Adopted Skills

| Skill | Agents | Governance | Purpose |
|-------|--------|------------|---------|
| `git-conventions` | DEV, REVIEWER | No | Branch naming, commit format, MR conventions |
| `review-criteria` | REVIEWER, DEV | **Yes** | Severity taxonomy, quality checklist, approval rules |
| `specification-template` | PO, QA | No | Structured spec format with acceptance criteria |

### Progressive Disclosure

1. `SkillPort.resolveSkills(role)` → returns lightweight `SkillDescriptor` (name + description)
2. `SkillPort.loadSkillContent(name)` → loads full markdown content on demand
3. `SpringAiAgentBridge` injects applicable skills into the LLM system prompt

Governance-flagged skills (e.g., `review-criteria`) are marked `[GOVERNANCE — compliance mandatory]` in the system prompt to signal the LLM that deviation is not permitted.

---

## Conversation History

Every agent interaction is recorded in a `ConversationHistory` aggregate scoped to a Wave:

- **AgentMessage**: sender, recipient, type (`MessageType` enum), content, timestamp
- Messages form an audit trail of the entire wave execution
- Types: `SPECIFICATION_READY`, `IMPLEMENTATION_COMPLETE`, `REVIEW_COMPLETE`, `QA_COMPLETE`, `GOVERNANCE_CHECK`, etc.

---

## External Integrations (via MCP)

All external tool access is abstracted behind domain ports and implemented as MCP client adapters:

| Port | MCP Server | Operations |
|------|-----------|------------|
| `ConfluencePort` | Confluence MCP | Fetch/create/update pages, list space pages |
| `GitLabPort` | GitLab MCP | Create branches, create/merge MRs, add comments |
| `JiraPort` | Jira MCP | Create issues, update status, manage sprints |
| `SonarQubePort` | SonarQube MCP | Get quality gate status, analyze projects |

---

## Tech Stack

- **Java 17** + **Spring Boot 4.1**
- **Spring AI 2.0** (Anthropic/Claude model, MCP client)
- **MySQL** (persistence) + **Flyway** (migrations)
- **Lombok** (boilerplate reduction)
- **Testcontainers** (integration tests with MySQL)
- **Maven** (build)

---

## Entry Points

| Endpoint | Method | Description |
|----------|--------|-------------|
| `POST /api/waves` | Start a new wave | Body: `{ waveName, taskDescription }` |
| `GET /api/waves/{id}` | Get wave status | Returns status, transitions, task/spec counts |
| `GET /api/waves` | List all waves | Summary view of all waves |

---

## Key Design Decisions

1. **Event-driven, not orchestrator-controlled**: Agents react to events, enabling loose coupling and async execution
2. **Governance is non-negotiable**: ReviewGate has no bypass — both reviewer AND SonarQube must approve
3. **Skills over prompts**: Reusable procedural knowledge lives in versioned `skills/` files, not hardcoded in prompts
4. **Ports in domain**: External integrations are expressed as domain interfaces, keeping the domain pure
5. **Conversation as audit**: Every inter-agent message is persisted for traceability and debugging
