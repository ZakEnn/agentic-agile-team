package com.agile.team.infrastructure.adapter.confluence;

import com.agile.team.domain.port.ConfluencePageContext;
import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.ConfluenceSearchResult;
import com.agile.team.infrastructure.config.ConfluenceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Direct REST API implementation of ConfluencePort using Bearer token authentication.
 * Replaces the MCP-based ConfluenceAdapter.
 */
@Component
@Primary
@EnableConfigurationProperties(ConfluenceProperties.class)
public class ConfluenceRestClient implements ConfluencePort {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceRestClient.class);
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConfluenceProperties properties;

    public ConfluenceRestClient(ConfluenceProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ConfluenceSearchResult searchPagesStructured(String spaceKey, String keyword) {
        log.info("Confluence REST: searching space='{}' keyword='{}'", spaceKey, keyword);

        if (keyword == null || keyword.isBlank()) {
            log.warn("Confluence REST: empty keyword provided, returning empty result");
            return new ConfluenceSearchResult(keyword, spaceKey, List.of());
        }

        try {
            String cql = String.format("text ~ '\"%s\"' AND type = 'page' AND space = '%s'", keyword, spaceKey);

            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", DEFAULT_MAX_RESULTS)
                            .queryParam("expand", "content.body.view")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Confluence REST: empty response body for keyword='{}'", keyword);
                return new ConfluenceSearchResult(keyword, spaceKey, List.of());
            }

            // Detect redirect to login page (HTML response instead of JSON)
            if (responseBody.trim().startsWith("<") || responseBody.contains("login.action")) {
                log.error("Confluence REST: received HTML/login redirect instead of JSON. " +
                        "PAT may lack permissions or Confluence requires session auth. keyword='{}'", keyword);
                return new ConfluenceSearchResult(keyword, spaceKey, List.of());
            }

            // Detect rate limiting
            if (responseBody.contains("Rate limit exceeded")) {
                log.warn("Confluence REST: rate limited. keyword='{}'", keyword);
                return new ConfluenceSearchResult(keyword, spaceKey, List.of());
            }

            List<ConfluencePageContext> pages = parseSearchResponse(responseBody);
            log.info("Confluence REST: found {} pages for keyword='{}'", pages.size(), keyword);
            return new ConfluenceSearchResult(keyword, spaceKey, pages);

        } catch (Exception e) {
            if (isAuthError(e)) {
                log.error("Confluence REST: authentication failed (401/403). PAT may be invalid or expired. keyword='{}'", keyword, e);
            } else {
                log.error("Confluence REST: search failed for space='{}' keyword='{}': {}", spaceKey, keyword, e.getMessage(), e);
            }
            return new ConfluenceSearchResult(keyword, spaceKey, List.of());
        }
    }

    @Override
    public Optional<String> searchPages(String spaceKey, String keyword) {
        ConfluenceSearchResult result = searchPagesStructured(spaceKey, keyword);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.toConcatenatedContent());
    }

    @Override
    public Optional<String> fetchPageContent(String pageId) {
        log.info("Confluence REST: fetching page content for id='{}'", pageId);
        try {
            String responseBody = restClient.get()
                    .uri("/rest/api/content/{pageId}?expand=body.view", pageId)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String htmlContent = root.path("body").path("view").path("value").asText("");
            String plainText = HtmlToTextConverter.convert(htmlContent);
            return Optional.ofNullable(plainText).filter(s -> !s.isBlank());

        } catch (Exception e) {
            log.error("Confluence REST: failed to fetch page id='{}': {}", pageId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<String> listPagesInSpace(String spaceKey) {
        log.info("Confluence REST: listing pages in space='{}'", spaceKey);
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content")
                            .queryParam("spaceKey", spaceKey)
                            .queryParam("type", "page")
                            .queryParam("limit", 50)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (responseBody == null) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");
            List<String> titles = new ArrayList<>();
            for (JsonNode page : results) {
                titles.add(page.path("title").asText());
            }
            return titles;

        } catch (Exception e) {
            log.error("Confluence REST: failed to list pages in space='{}': {}", spaceKey, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public String createPage(String spaceKey, String title, String content) {
        log.warn("Confluence REST: createPage not yet implemented via REST API");
        return "";
    }

    @Override
    public void updatePage(String pageId, String content) {
        log.warn("Confluence REST: updatePage not yet implemented via REST API");
    }

    private List<ConfluencePageContext> parseSearchResponse(String responseBody) {
        List<ConfluencePageContext> pages = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");

            for (JsonNode result : results) {
                JsonNode content = result.path("content");
                String pageId = content.path("id").asText("");
                String title = content.path("title").asText("");

                // Build page URL
                String webUi = content.path("_links").path("webui").asText("");
                String url = webUi.isEmpty() ? "" : properties.baseUrl() + webUi;

                // Extract body content (expanded via ?expand=content.body.view)
                String htmlBody = content.path("body").path("view").path("value").asText("");
                String plainText = HtmlToTextConverter.convert(htmlBody);

                // If body not expanded in search result, use the excerpt
                if (plainText.isBlank()) {
                    String excerpt = result.path("excerpt").asText("");
                    plainText = HtmlToTextConverter.convert(excerpt);
                }

                if (!pageId.isBlank()) {
                    pages.add(new ConfluencePageContext(pageId, title, url, plainText));
                }
            }
        } catch (Exception e) {
            log.error("Confluence REST: failed to parse search response: {}", e.getMessage(), e);
        }
        return pages;
    }

    private boolean isAuthError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("401") || message.contains("403"));
    }
}
