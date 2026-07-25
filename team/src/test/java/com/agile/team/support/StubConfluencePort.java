package com.agile.team.support;

import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.ConfluenceSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link ConfluencePort} so the Spec agent's retrieval path can be tested
 * — including the failure path, which is the one that matters: a retrieval outage
 * must degrade the spec and say so, not fail the stage or silently pretend the
 * documentation was empty.
 */
public class StubConfluencePort implements ConfluencePort {

    private String searchResult;
    private RuntimeException searchFailure;
    private final List<String> searchedSpaces = new ArrayList<>();
    private final List<String> searchedKeywords = new ArrayList<>();

    public StubConfluencePort returning(String content) {
        this.searchResult = content;
        return this;
    }

    public StubConfluencePort returningNothing() {
        this.searchResult = null;
        return this;
    }

    public StubConfluencePort failingWith(RuntimeException exception) {
        this.searchFailure = exception;
        return this;
    }

    @Override
    public Optional<String> searchPages(String spaceKey, String keyword) {
        searchedSpaces.add(spaceKey);
        searchedKeywords.add(keyword);
        if (searchFailure != null) {
            throw searchFailure;
        }
        return Optional.ofNullable(searchResult);
    }

    @Override
    public ConfluenceSearchResult searchPagesStructured(String spaceKey, String keyword) {
        return new ConfluenceSearchResult(keyword, spaceKey, List.of());
    }

    @Override
    public Optional<String> fetchPageContent(String pageId) {
        return Optional.ofNullable(searchResult);
    }

    @Override
    public List<String> listPagesInSpace(String spaceKey) {
        return List.of();
    }

    @Override
    public String createPage(String spaceKey, String title, String content) {
        return "stub-page-id";
    }

    @Override
    public void updatePage(String pageId, String content) {
        // no-op
    }

    public List<String> searchedSpaces() {
        return List.copyOf(searchedSpaces);
    }

    public List<String> searchedKeywords() {
        return List.copyOf(searchedKeywords);
    }

    public boolean wasSearched() {
        return !searchedKeywords.isEmpty();
    }
}
