package com.agile.team.application.tool;

import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.ConfluenceSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool exposing Confluence search capabilities to the LLM.
 * Registered with the ChatClient so agents can invoke it via tool-calling.
 */
@Component
public class ConfluenceTools {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceTools.class);
    private static final String DEFAULT_SPACE_KEY = "EPE";

    private final ConfluencePort confluencePort;

    public ConfluenceTools(ConfluencePort confluencePort) {
        this.confluencePort = confluencePort;
    }

    @Tool(description = "Searches Confluence space EPE for pages matching a keyword and returns their content as structured results. " +
            "Use this to find documentation, specifications, or technical context related to a topic.")
    public ConfluenceSearchResult searchConfluencePages(
            @ToolParam(description = "The keyword or phrase to search for in Confluence page content") String keyword
    ) {
        log.info("Tool invoked: searchConfluencePages(keyword='{}')", keyword);
        return confluencePort.searchPagesStructured(DEFAULT_SPACE_KEY, keyword);
    }
}
