package com.agile.team.infrastructure.adapter.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads versioned prompt templates from {@code classpath:/prompts/}.
 * <p>
 * Prompts were previously inline {@code StringBuilder} and {@code String.format}
 * calls scattered through handlers, which made them impossible to diff, review or
 * test independently of the code around them. As files they are reviewable
 * artifacts — and a prompt change shows up in a pull request like any other change.
 * <p>
 * Substitution is deliberately a plain {@code ${name}} string replace rather than a
 * templating engine. Prompt values here include <strong>code diffs</strong>, which
 * are full of braces, dollar signs and backslashes; handing that to StringTemplate
 * or a regex-based engine invites a rendering error or a silent mangling on exactly
 * the input that matters most. A literal replace cannot misinterpret its payload.
 */
@Component
public class PromptTemplates {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Load a template by name, e.g. {@code "review"} for {@code /prompts/review.md}. */
    public String load(String name) {
        return cache.computeIfAbsent(name, n -> {
            ClassPathResource resource = new ClassPathResource("prompts/" + n + ".md");
            if (!resource.exists()) {
                throw new IllegalStateException("Prompt template not found: prompts/" + n + ".md");
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read prompt template " + n, e);
            }
        });
    }

    /**
     * Render a template. Missing values become empty strings rather than leaving a
     * literal {@code ${placeholder}} in the prompt — a visible placeholder reads to
     * the model as content and has produced some memorably confused output.
     */
    public String render(String name, Map<String, String> values) {
        String template = load(name);
        Map<String, String> safe = new HashMap<>(values != null ? values : Map.of());
        StringBuilder out = new StringBuilder(template.length() + 256);

        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("${", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf('}', start);
            if (end < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            String key = template.substring(start + 2, end);
            String value = safe.get(key);
            out.append(value != null ? value : "");
            i = end + 1;
        }
        return out.toString();
    }
}
