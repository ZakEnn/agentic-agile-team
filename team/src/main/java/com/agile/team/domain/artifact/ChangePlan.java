package com.agile.team.domain.artifact;

import java.util.List;

/**
 * What the Developer agent proposes to change. The model's output, before anything
 * is written to disk or built.
 * <p>
 * Separating the <em>plan</em> from the {@link Implementation} result is the point:
 * the model produces this, and the pipeline decides whether it survives contact with
 * a compiler. A model cannot report that its own change builds.
 *
 * @param rationale short explanation of the approach, for the audit trail
 * @param changes   full new content per file; empty is invalid — a Developer stage
 *                  that changes nothing has not implemented anything
 */
public record ChangePlan(String rationale, List<FileEdit> changes) {

    public ChangePlan {
        if (rationale == null || rationale.isBlank()) {
            throw new ArtifactValidationException("ChangePlan.rationale must not be blank");
        }
        if (changes == null || changes.isEmpty()) {
            throw new ArtifactValidationException(
                    "ChangePlan.changes must not be empty — a change plan that edits nothing "
                            + "is not an implementation");
        }
        changes = List.copyOf(changes);
    }

    public record FileEdit(String path, String content) {
        public FileEdit {
            if (path == null || path.isBlank()) {
                throw new ArtifactValidationException("FileEdit.path must not be blank");
            }
            // Rejected here as well as in the executor. Defence in depth: the executor
            // is the enforcement point, but catching it at the artifact boundary means
            // the attempt is visible in the audit trail as a validation failure.
            if (path.contains("..")) {
                throw new ArtifactValidationException(
                        "FileEdit.path must not contain '..': " + path);
            }
            if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
                throw new ArtifactValidationException(
                        "FileEdit.path must be relative to the workspace: " + path);
            }
            if (content == null) content = "";
        }
    }

    public List<String> changedPaths() {
        return changes.stream().map(FileEdit::path).toList();
    }
}
