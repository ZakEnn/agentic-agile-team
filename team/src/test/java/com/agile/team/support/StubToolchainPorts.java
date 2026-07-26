package com.agile.team.support;

import com.agile.team.domain.port.*;
import com.agile.team.domain.review.CodeQualityScore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory GitLab / Jira / SonarQube ports.
 * <p>
 * Deliberate design point: these are <em>deterministic</em> stubs of
 * <em>deterministic</em> ports. Because no domain port is backed by a model, the
 * whole toolchain layer can be faked with plain objects, and the reviewer's degraded
 * paths (no Jira key, Sonar unreachable) are exercisable rather than theoretical.
 */
public final class StubToolchainPorts {

    private StubToolchainPorts() {
    }

    public static class Git implements GitLabPort {
        private MergeRequestSnapshot snapshot;
        private RuntimeException failure;
        public final List<String> postedComments = new ArrayList<>();

        public Git returning(MergeRequestSnapshot snapshot) {
            this.snapshot = snapshot;
            return this;
        }

        public Git failingWith(RuntimeException e) {
            this.failure = e;
            return this;
        }

        @Override
        public Optional<MergeRequestSnapshot> fetchMergeRequest(String projectId, String iid) {
            if (failure != null) throw failure;
            return Optional.ofNullable(snapshot);
        }

        @Override public String createBranch(String p, String b, String s) { return b; }
        @Override public String createMergeRequest(String p, String s, String t, String ti, String d) { return "1"; }
        @Override public Optional<String> getMergeRequestStatus(String p, String m) { return Optional.of("opened"); }
        @Override public void mergeMergeRequest(String p, String m) { }
        @Override public void addMergeRequestComment(String p, String m, String comment) {
            postedComments.add(comment);
        }
    }

    public static class Jira implements JiraPort {
        private JiraIssueSnapshot issue;

        public Jira returning(JiraIssueSnapshot issue) {
            this.issue = issue;
            return this;
        }

        @Override public Optional<JiraIssueSnapshot> fetchIssue(String key) {
            return Optional.ofNullable(issue);
        }
        @Override public String createIssue(String p, String s, String d, String t) { return p + "-1"; }
        @Override public void updateIssueStatus(String k, String s) { }
        @Override public Optional<String> getIssueStatus(String k) { return Optional.empty(); }
        @Override public List<String> getSprintIssues(String s) { return List.of(); }
        @Override public void addComment(String k, String c) { }
    }

    public static class Sonar implements SonarQubePort {
        private CodeQualityScore score = CodeQualityScore.unknown();

        public Sonar returning(CodeQualityScore score) {
            this.score = score;
            return this;
        }

        @Override public Optional<CodeQualityScore> getQualityGateStatus(String projectKey) {
            return Optional.of(score);
        }
        @Override public Optional<CodeQualityScore> analyzeProject(String projectKey) {
            return Optional.of(score);
        }
    }
}
