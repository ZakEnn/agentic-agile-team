package com.agile.team.domain.port;

/**
 * Represents a single Confluence page returned from a search.
 */
public record ConfluencePageContext(
        String pageId,
        String title,
        String url,
        String plainTextContent
) {
}
