package com.agile.team.domain.wave;

/**
 * Per-wave targeting: which Confluence space, which GitLab project, which Jira
 * project, which language.
 * <p>
 * Before this existed, all four were compile-time constants scattered across
 * handlers — {@code CONFLUENCE_SPACE_KEY = "EPE"} in {@code PoAgentHandler},
 * {@code "epe-rating-ftth-passive"} inlined in the DEV prompt, Jira project
 * {@code "SCA"}, and a default language of French. That made the system a bespoke
 * script for one project rather than a platform, and made it untestable against
 * anything else.
 *
 * @param confluenceSpaceKey space to search for requirement context, e.g. {@code EPE}
 * @param gitLabProject      project path or id the Developer/Reviewer agents act on
 * @param jiraProjectKey     project key new issues are created under, e.g. {@code SCA}
 * @param language           natural language for generated artifacts, e.g. {@code French}
 */
public record WaveContext(
        String confluenceSpaceKey,
        String gitLabProject,
        String jiraProjectKey,
        String language
) {

    public static final String DEFAULT_LANGUAGE = "English";

    public WaveContext {
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE;
        }
    }

    /**
     * Context with only a Confluence space — enough for the spec stage, which is
     * the only stage that can run before a target repository is known.
     */
    public static WaveContext forSpecOnly(String confluenceSpaceKey, String language) {
        return new WaveContext(confluenceSpaceKey, null, null, language);
    }

    public boolean hasGitLabProject() {
        return gitLabProject != null && !gitLabProject.isBlank();
    }

    public boolean hasJiraProject() {
        return jiraProjectKey != null && !jiraProjectKey.isBlank();
    }

    public boolean hasConfluenceSpace() {
        return confluenceSpaceKey != null && !confluenceSpaceKey.isBlank();
    }
}
