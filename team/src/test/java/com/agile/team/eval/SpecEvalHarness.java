package com.agile.team.eval;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.SpecAgent;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.LlmGateway;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import com.agile.team.support.StubConfluencePort;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Spec agent over the graded case set and reports scores.
 * <p>
 * Deliberately gateway-agnostic: pass a {@code ScriptedLlmGateway} to exercise the
 * rubric deterministically in CI, or the real {@code SpringAiLlmGateway} to measure
 * an actual model. Same cases, same rubric, so a prompt or model change can be
 * compared against a baseline instead of eyeballed.
 */
public class SpecEvalHarness {

    private static final String CASES_RESOURCE = "/eval/spec-cases.json";

    private final LlmGateway gateway;
    private final SpecEvalScorer scorer = new SpecEvalScorer();

    public SpecEvalHarness(LlmGateway gateway) {
        this.gateway = gateway;
    }

    public static List<SpecEvalCase> loadCases() {
        try (InputStream in = SpecEvalHarness.class.getResourceAsStream(CASES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Eval cases not found at " + CASES_RESOURCE);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonMapper.builder().build()
                    .readValue(json, new TypeReference<List<SpecEvalCase>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load eval cases", e);
        }
    }

    /** Run every case and return its score. */
    public List<SpecEvalScore> runAll() {
        List<SpecEvalScore> scores = new ArrayList<>();
        for (SpecEvalCase evalCase : loadCases()) {
            scores.add(run(evalCase));
        }
        return scores;
    }

    public SpecEvalScore run(SpecEvalCase evalCase) {
        StubConfluencePort confluence = new StubConfluencePort();
        if (!evalCase.documentation().isBlank()) {
            confluence.returning(evalCase.documentation());
        } else {
            confluence.returningNothing();
        }

        SpecAgent agent = new SpecAgent(confluence, gateway, new SkillContextResolver(new NoSkills()));

        AgentOutcome<SpecDraft> outcome = agent.run(new SpecAgent.SpecAgentRequest(
                "eval-" + evalCase.name(),
                evalCase.intent(),
                evalCase.keyword(),
                new WaveContext("EVAL", null, null, "English")));

        return scorer.score(evalCase, outcome.artifact());
    }

    /** Summary line suitable for CI output or a baseline file. */
    public static String summarise(List<SpecEvalScore> scores) {
        long passed = scores.stream().filter(SpecEvalScore::passed).count();
        StringBuilder sb = new StringBuilder();
        sb.append("Spec eval: ").append(passed).append('/').append(scores.size()).append(" passed");
        scores.forEach(s -> sb.append('\n').append(s.describe()));
        return sb.toString();
    }

    private static class NoSkills implements SkillPort {
        @Override public List<SkillDescriptor> resolveSkills(AgentRole role) { return List.of(); }
        @Override public String loadSkillContent(String skillName) { return ""; }
    }
}
