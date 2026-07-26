package com.agile.team.infrastructure.adapter.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates();

    @Test
    void shouldRenderPlaceholders() {
        String rendered = templates.render("review", Map.of(
                "projectId", "epe/app", "mergeRequestIid", "42", "title", "A title"));

        assertTrue(rendered.contains("epe/app"));
        assertTrue(rendered.contains("!42"));
        assertFalse(rendered.contains("${projectId}"));
    }

    @Test
    void shouldSubstituteMissingValuesWithEmptyStringNotALiteralPlaceholder() {
        // A leftover ${author} in the prompt reads to the model as content.
        String rendered = templates.render("review", Map.of("projectId", "p"));
        assertFalse(rendered.contains("${author}"));
        assertFalse(rendered.contains("${diff}"));
    }

    @Test
    void shouldRenderDiffsContainingBracesAndDollarsUnharmed() {
        // The reason substitution is a literal replace rather than a templating
        // engine: real diffs are full of braces, dollars and backslashes, and this is
        // precisely the input that must not be misinterpreted.
        String hostileDiff = """
                + public void x() { if (a) { b(); } }
                + String s = "${notAPlaceholder}" + '\\n';
                + Map<String,String> m = Map.of("k", "v");
                """;

        String rendered = templates.render("review", Map.of("diff", hostileDiff));

        assertTrue(rendered.contains("${notAPlaceholder}"),
                "content inside a substituted value must pass through verbatim");
        assertTrue(rendered.contains("Map<String,String>"));
        assertTrue(rendered.contains("if (a) { b(); }"));
    }

    @Test
    void shouldTolerateAnUnterminatedPlaceholder() {
        assertDoesNotThrow(() -> templates.render("review", Map.of()));
    }

    @Test
    void shouldFailLoudlyForAMissingTemplate() {
        assertThrows(IllegalStateException.class, () -> templates.load("no-such-template"));
    }

    @Test
    void shouldCacheLoadedTemplates() {
        assertSame(templates.load("review"), templates.load("review"));
    }

    @Test
    void shouldCarryTheInstructionThatTheModelMustNotStateADisposition() {
        // Load-bearing prompt content: approval is computed from severities.
        assertTrue(templates.load("review").contains("Do not state a disposition"));
    }
}
