package com.agile.team.domain.port;

import java.util.List;
import java.util.Optional;

public interface ConfluencePort {

    Optional<String> fetchPageContent(String pageId);

    List<String> listPagesInSpace(String spaceKey);

    /**
     * Searches for pages in the given Confluence space whose content matches the keyword.
     * Returns the concatenated content of matching pages, or empty if no results found.
     */
    Optional<String> searchPages(String spaceKey, String keyword);

    /**
     * Searches for pages and returns structured results with metadata and clean text content.
     */
    ConfluenceSearchResult searchPagesStructured(String spaceKey, String keyword);

    String createPage(String spaceKey, String title, String content);

    void updatePage(String pageId, String content);
}
