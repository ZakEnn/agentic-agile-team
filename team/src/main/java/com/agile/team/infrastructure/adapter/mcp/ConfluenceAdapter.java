package com.agile.team.infrastructure.adapter.mcp;

import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.ConfluenceSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// TODO: remove once REST-based Confluence integration is validated
// This MCP-based adapter is replaced by ConfluenceRestClient (@Primary).
// Kept for rollback reference only.
@Component
public class ConfluenceAdapter implements ConfluencePort {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceAdapter.class);

    // private final ChatClient chatClient;

    public ConfluenceAdapter(ChatClient.Builder chatClientBuilder) {
        // this.chatClient = chatClientBuilder.build();
        log.info("ConfluenceAdapter (MCP-based) instantiated but INACTIVE — ConfluenceRestClient is @Primary");
    }

    @Override
    public Optional<String> fetchPageContent(String pageId) {
        // TODO: remove once REST-based Confluence integration is validated
        // String response = chatClient.prompt()
        //         .user("Fetch the content of Confluence page with ID: " + pageId +
        //               ". Return the full page content as-is without any summary or commentary.")
        //         .call()
        //         .content();
        // return Optional.ofNullable(response).filter(s -> !s.isBlank());
        log.warn("ConfluenceAdapter.fetchPageContent called but MCP integration is disabled");
        return Optional.empty();
    }

    @Override
    public List<String> listPagesInSpace(String spaceKey) {
        // TODO: remove once REST-based Confluence integration is validated
        // String response = chatClient.prompt()
        //         .user("List all pages in Confluence space: " + spaceKey)
        //         .call()
        //         .content();
        // return response != null ? List.of(response.split("\n")) : List.of();
        log.warn("ConfluenceAdapter.listPagesInSpace called but MCP integration is disabled");
        return List.of();
    }

    @Override
    public Optional<String> searchPages(String spaceKey, String keyword) {
        // TODO: remove once REST-based Confluence integration is validated
        // log.info("Confluence searchPages(space={}, keyword={})", spaceKey, keyword);
        // try {
        //     String response = chatClient.prompt()
        //             .user(String.format(
        //                     "Search for pages in Confluence space '%s' that match the keyword '%s'. " +
        //                     "Return the full content of all matching pages concatenated together. " +
        //                     "If no pages match, respond with an empty string.",
        //                     spaceKey, keyword))
        //             .call()
        //             .content();
        //     return Optional.ofNullable(response).filter(s -> !s.isBlank());
        // } catch (Exception e) {
        //     log.error("Confluence searchPages failed: {}", e.getMessage(), e);
        //     return Optional.empty();
        // }
        log.warn("ConfluenceAdapter.searchPages called but MCP integration is disabled");
        return Optional.empty();
    }

    @Override
    public ConfluenceSearchResult searchPagesStructured(String spaceKey, String keyword) {
        log.warn("ConfluenceAdapter.searchPagesStructured called but MCP integration is disabled");
        return new ConfluenceSearchResult(keyword, spaceKey, List.of());
    }

    @Override
    public String createPage(String spaceKey, String title, String content) {
        // TODO: remove once REST-based Confluence integration is validated
        log.warn("ConfluenceAdapter.createPage called but MCP integration is disabled");
        return "";
    }

    @Override
    public void updatePage(String pageId, String content) {
        // TODO: remove once REST-based Confluence integration is validated
        log.warn("ConfluenceAdapter.updatePage called but MCP integration is disabled");
    }
}
