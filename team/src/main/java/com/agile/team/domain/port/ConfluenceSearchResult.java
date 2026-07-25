package com.agile.team.domain.port;

import java.util.List;

/**
 * Structured result of a Confluence search operation.
 */
public record ConfluenceSearchResult(
        String keyword,
        String spaceKey,
        List<ConfluencePageContext> pages
) {

    public boolean isEmpty() {
        return pages == null || pages.isEmpty();
    }

    /**
     * Returns all page content concatenated, suitable for LLM context injection.
     */
    public String toConcatenatedContent() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ConfluencePageContext page : pages) {
            sb.append("=== ").append(page.title()).append(" ===\n");
            sb.append(page.plainTextContent()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
