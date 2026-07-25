package com.agile.team.application.orchestrator;

import com.agile.team.application.agent.SpecAgent;
import com.agile.team.application.gate.GatePolicy;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.gate.GateMode;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationStatus;
import com.agile.team.domain.wave.*;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import com.agile.team.infrastructure.config.SdlcProperties;
import com.agile.team.support.ScriptedLlmGateway;
import com.agile.team.support.StubConfluencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The M1 vertical slice, end to end: intent → drafted spec → approval gate →
 * wave starts execution.
 * <p>
 * Uses in-memory repositories and a scripted gateway, so it runs in milliseconds
 * with no database, no network and no API key — which is what makes it usable as a
 * regression gate on every commit.
 */
class OrchestratorSpecStageTest {

    private InMemoryWaveRepository waves;
    private InMemoryConversationRepository conversations;
    private ScriptedLlmGateway llm;
    private StubConfluencePort confluence;
    private SdlcProperties properties;

    private static final SpecDraft DRAFT = new SpecDraft(
            "Add retry to SFTP poller",
            "Retry transient failures with exponential backoff.",
            List.of("Given a transient failure, when polling, then retry up to 3 times",
                    "Given 3 consecutive failures, when polling, then raise an alert"),
            List.of("Changing the polling schedule"));

    @BeforeEach
    void setUp() {
        waves = new InMemoryWaveRepository();
        conversations = new InMemoryConversationRepository();
        llm = new ScriptedLlmGateway();
        confluence = new StubConfluencePort();
        properties = new SdlcProperties();
    }

    @Test
    void shouldParkAtTheApprovalGateWhenApprovalIsRequired() {
        gate(GateName.SPEC_APPROVAL, GateMode.REQUIRED);
        confluence.returning("Existing poller docs");
        llm.respondWith(DRAFT);

        WaveId waveId = orchestrator().startWave(command());

        Wave wave = waves.findById(waveId).orElseThrow();
        assertEquals(WaveStatus.PLANNING, wave.getStatus(),
                "wave must not advance while a required gate is undecided");
        assertEquals(SpecificationStatus.DRAFT, wave.getSpecifications().get(0).getStatus());
        assertTrue(conversationTypes(waveId).contains(MessageType.AWAITING_APPROVAL));
    }

    @Test
    void shouldAdvanceAutomaticallyWhenPolicyAutoApproves() {
        gate(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);
        llm.respondWith(DRAFT);

        WaveId waveId = orchestrator().startWave(command());

        Wave wave = waves.findById(waveId).orElseThrow();
        assertEquals(WaveStatus.IN_PROGRESS, wave.getStatus());
        Specification spec = wave.getSpecifications().get(0);
        assertEquals(SpecificationStatus.APPROVED, spec.getStatus());
        assertEquals("AUTO:spec-approval", spec.getDecidedBy(),
                "an automated approval must stay identifiable in the audit trail");
    }

    @Test
    void shouldPersistTheStructuredAcceptanceCriteria() {
        // The specific regression this guards: the old handler stored the raw model
        // response and passed List.of() for criteria, throwing away the only thing
        // the QA stage can verify against.
        gate(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);
        llm.respondWith(DRAFT);

        WaveId waveId = orchestrator().startWave(command());

        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);
        assertEquals(2, spec.getAcceptanceCriteria().size());
        assertEquals(1, spec.getOutOfScope().size());
        assertEquals("Add retry to SFTP poller", spec.getTitle());
    }

    @Test
    void shouldStartExecutionWhenAHumanApproves() {
        gate(GateName.SPEC_APPROVAL, GateMode.REQUIRED);
        llm.respondWith(DRAFT);
        Orchestrator orchestrator = orchestrator();
        WaveId waveId = orchestrator.startWave(command());
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);

        Wave wave = orchestrator.decideSpecification(waveId, spec.getId(),
                com.agile.team.domain.gate.GateDecision.approvedBy(
                        GateName.SPEC_APPROVAL, "alice", "looks right"));

        assertEquals(WaveStatus.IN_PROGRESS, wave.getStatus());
        assertEquals("alice", wave.getTransitions().get(0).authorizedBy());
        assertTrue(conversationTypes(waveId).contains(MessageType.GATE_DECISION));
    }

    @Test
    void shouldFailTheWaveWhenAHumanRejects() {
        gate(GateName.SPEC_APPROVAL, GateMode.REQUIRED);
        llm.respondWith(DRAFT);
        Orchestrator orchestrator = orchestrator();
        WaveId waveId = orchestrator.startWave(command());
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);

        Wave wave = orchestrator.decideSpecification(waveId, spec.getId(),
                com.agile.team.domain.gate.GateDecision.rejectedBy(
                        GateName.SPEC_APPROVAL, "alice", "criteria are not testable"));

        assertEquals(WaveStatus.FAILED, wave.getStatus());
        assertEquals(SpecificationStatus.REJECTED, wave.getSpecifications().get(0).getStatus());
    }

    @Test
    void shouldApplyAHumanEditBeforeApproval() {
        gate(GateName.SPEC_APPROVAL, GateMode.REQUIRED);
        llm.respondWith(DRAFT);
        Orchestrator orchestrator = orchestrator();
        WaveId waveId = orchestrator.startWave(command());
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);

        SpecDraft edited = new SpecDraft("Edited title", "Edited description",
                List.of("A sharper criterion"), List.of());
        Wave wave = orchestrator.editSpecification(waveId, spec.getId(), edited, "alice");

        Specification updated = wave.getSpecifications().get(0);
        assertEquals("Edited title", updated.getTitle());
        assertEquals(List.of("A sharper criterion"), updated.getAcceptanceCriteria());
        assertEquals(SpecificationStatus.DRAFT, updated.getStatus());
    }

    @Test
    void shouldRefuseToEditAnAlreadyDecidedSpecification() {
        // Editing after approval would leave the decision record describing something
        // that is no longer what was approved.
        gate(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);
        llm.respondWith(DRAFT);
        Orchestrator orchestrator = orchestrator();
        WaveId waveId = orchestrator.startWave(command());
        Specification spec = waves.findById(waveId).orElseThrow().getSpecifications().get(0);

        assertThrows(IllegalStateException.class, () -> orchestrator.editSpecification(
                waveId, spec.getId(),
                new SpecDraft("x", "y", List.of("z")), "alice"));
    }

    @Test
    void shouldFailTheWaveWhenTheSpecStageThrows() {
        gate(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);
        llm.failWith(new com.agile.team.domain.artifact.ArtifactValidationException("no criteria"));

        Orchestrator orchestrator = orchestrator();
        assertThrows(com.agile.team.domain.artifact.ArtifactValidationException.class,
                () -> orchestrator.startWave(command()));

        Wave wave = waves.all().get(0);
        assertEquals(WaveStatus.FAILED, wave.getStatus());
    }

    @Test
    void shouldRecordRetrievalGapsInTheAuditTrail() {
        gate(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);
        confluence.failingWith(new RuntimeException("connection refused"));
        llm.respondWith(DRAFT);

        WaveId waveId = orchestrator().startWave(command());

        assertTrue(conversations.findByWaveId(waveId).orElseThrow().getMessages().stream()
                        .anyMatch(m -> m.payload().contains("Confluence retrieval failed")),
                "a human at the gate must be able to see the spec was drafted blind");
    }

    // --- helpers ---

    private Orchestrator orchestrator() {
        SpecAgent agent = new SpecAgent(confluence, llm, new SkillContextResolver(new NoSkills()));
        return new Orchestrator(waves, new SeededAgentRepository(), conversations, agent,
                new GatePolicy(properties));
    }

    private Orchestrator.StartWaveCommand command() {
        return new Orchestrator.StartWaveCommand("Sprint 1",
                "Make the SFTP poller resilient", "SFTP retry",
                new WaveContext("EPE", "epe-rating-ftth-passive", "SCA", "English"));
    }

    private void gate(GateName gate, GateMode mode) {
        SdlcProperties.GateConfig config = new SdlcProperties.GateConfig();
        config.setMode(mode);
        properties.getGates().put(gate.configKey(), config);
    }

    private List<MessageType> conversationTypes(WaveId waveId) {
        return conversations.findByWaveId(waveId).orElseThrow().getMessages().stream()
                .map(m -> m.type()).toList();
    }

    private static class InMemoryWaveRepository implements WaveRepository {
        private final Map<UUID, Wave> store = new LinkedHashMap<>();

        @Override public Wave save(Wave wave) { store.put(wave.getId().value(), wave); return wave; }
        @Override public Optional<Wave> findById(WaveId id) { return Optional.ofNullable(store.get(id.value())); }
        @Override public List<Wave> findByStatus(WaveStatus status) {
            return store.values().stream().filter(w -> w.getStatus() == status).toList();
        }
        @Override public List<Wave> findAll() { return new ArrayList<>(store.values()); }
        List<Wave> all() { return new ArrayList<>(store.values()); }
    }

    private static class InMemoryConversationRepository implements ConversationRepository {
        private final Map<UUID, ConversationHistory> store = new LinkedHashMap<>();

        @Override public ConversationHistory save(ConversationHistory history) {
            store.put(history.getWaveId().value(), history);
            return history;
        }
        @Override public Optional<ConversationHistory> findByWaveId(WaveId waveId) {
            return Optional.ofNullable(store.get(waveId.value()));
        }
    }

    private static class SeededAgentRepository implements AgentRepository {
        private final List<Agent> agents = Arrays.stream(AgentRole.values())
                .map(role -> new Agent(AgentId.generate(), role.name() + " Agent", role))
                .toList();

        @Override public Agent save(Agent agent) { return agent; }
        @Override public Optional<Agent> findById(AgentId id) {
            return agents.stream().filter(a -> a.getId().equals(id)).findFirst();
        }
        @Override public List<Agent> findByRole(AgentRole role) {
            return agents.stream().filter(a -> a.getRole() == role).toList();
        }
        @Override public List<Agent> findAll() { return agents; }
    }

    private static class NoSkills implements SkillPort {
        @Override public List<SkillDescriptor> resolveSkills(AgentRole role) { return List.of(); }
        @Override public String loadSkillContent(String skillName) { return ""; }
    }
}
