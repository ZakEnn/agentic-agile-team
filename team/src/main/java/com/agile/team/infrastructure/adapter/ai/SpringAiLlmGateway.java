package com.agile.team.infrastructure.adapter.ai;

import com.agile.team.domain.artifact.ArtifactValidationException;
import com.agile.team.domain.port.LlmGateway;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * The real {@link LlmGateway}: Spring AI structured output binding, with token
 * accounting and per-stage metrics.
 * <p>
 * Replaces {@code SpringAiAgentBridge}, which returned raw strings that callers
 * then interrogated with {@code contains("approved")}. Here the model is asked for
 * a type, and a response that will not bind to that type is an error rather than
 * a string that happens to contain a hopeful word.
 * <p>
 * Note the deliberate absence of the old workaround: the previous bridge routed
 * <em>every</em> call through a tool-equipped client because
 * "claude-sonnet-5 uses extended thinking (which produces empty text response)
 * when tools are not present". Attaching unrelated tools to suppress a thinking
 * mode is a side effect standing in for configuration. Structured output binding
 * addresses the same symptom directly — the model is given a schema to fill.
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmGateway.class);

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;

    public SpringAiLlmGateway(ChatClient.Builder chatClientBuilder, MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <T> LlmResult<T> generate(LlmRequest request, Class<T> responseType) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String role = request.role().name();
        String stage = request.stage() != null ? request.stage() : "unknown";

        log.info("LLM call role={} stage={} wave={} type={} promptChars={}",
                role, stage, request.waveId(), responseType.getSimpleName(),
                request.userPrompt().length());

        try {
            ResponseEntity<ChatResponse, T> response = chatClient.prompt()
                    .system(request.systemPrompt())
                    .user(request.userPrompt())
                    .call()
                    .responseEntity(responseType);

            T entity = response.getEntity();
            if (entity == null) {
                throw new LlmGatewayException(
                        "Model returned no parsable " + responseType.getSimpleName()
                                + " for role=" + role + " stage=" + stage);
            }

            TokenUsage usage = extractUsage(response.getResponse());
            recordMetrics(role, stage, usage);

            log.info("LLM call complete role={} stage={} wave={} inputTokens={} outputTokens={}",
                    role, stage, request.waveId(), usage.inputTokens(), usage.outputTokens());

            return new LlmResult<>(entity, usage);

        } catch (ArtifactValidationException e) {
            // The model produced well-formed JSON that violates a domain invariant.
            // Surfaced unchanged so the caller can feed the message back as a retry.
            meterRegistry.counter("sdlc.llm.validation_failures", "role", role, "stage", stage).increment();
            log.warn("LLM artifact failed domain validation role={} stage={}: {}", role, stage, e.getMessage());
            throw e;
        } catch (LlmGatewayException e) {
            meterRegistry.counter("sdlc.llm.errors", "role", role, "stage", stage).increment();
            throw e;
        } catch (Exception e) {
            meterRegistry.counter("sdlc.llm.errors", "role", role, "stage", stage).increment();
            throw new LlmGatewayException(
                    "LLM call failed for role=" + role + " stage=" + stage + ": " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("sdlc.llm.duration")
                    .tag("role", role)
                    .tag("stage", stage)
                    .register(meterRegistry));
        }
    }

    private TokenUsage extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return TokenUsage.unknown();
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return TokenUsage.unknown();
        }
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        return TokenUsage.of(prompt != null ? prompt : 0, completion != null ? completion : 0);
    }

    private void recordMetrics(String role, String stage, TokenUsage usage) {
        Counter.builder("sdlc.llm.tokens")
                .tag("role", role).tag("stage", stage).tag("direction", "input")
                .register(meterRegistry)
                .increment(usage.inputTokens());
        Counter.builder("sdlc.llm.tokens")
                .tag("role", role).tag("stage", stage).tag("direction", "output")
                .register(meterRegistry)
                .increment(usage.outputTokens());
        meterRegistry.counter("sdlc.llm.calls", "role", role, "stage", stage).increment();
    }
}
