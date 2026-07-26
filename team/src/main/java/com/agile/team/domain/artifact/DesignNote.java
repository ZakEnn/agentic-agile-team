package com.agile.team.domain.artifact;

import java.util.List;

/**
 * The Architect agent's output: where and how, before any code exists.
 * <p>
 * {@code impactedModules} is the field that earns this stage its place. The plan
 * requires it to be <strong>verified against the real repository tree</strong>
 * before the design is accepted — a hallucinated module name that reaches the
 * Developer stage becomes a hallucinated file, and then a build failure three
 * stages later with no obvious cause.
 *
 * @param approach        how the change should be made
 * @param impactedModules paths or module names the change touches; verified, not trusted
 * @param risks           what could go wrong
 * @param testStrategy    how the acceptance criteria should be covered
 * @param adrRequired     whether this decision warrants an architecture decision record
 */
public record DesignNote(
        String approach,
        List<String> impactedModules,
        List<String> risks,
        String testStrategy,
        boolean adrRequired
) {
    public DesignNote {
        if (approach == null || approach.isBlank()) {
            throw new ArtifactValidationException("DesignNote.approach must not be blank");
        }
        if (impactedModules == null || impactedModules.isEmpty()) {
            throw new ArtifactValidationException(
                    "DesignNote.impactedModules must name at least one module — a design that "
                            + "touches nothing is not a design");
        }
        impactedModules = impactedModules.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(String::trim)
                .toList();
        if (impactedModules.isEmpty()) {
            throw new ArtifactValidationException("DesignNote.impactedModules contained only blanks");
        }
        risks = risks == null ? List.of() : List.copyOf(risks);
        if (testStrategy == null) testStrategy = "";
    }
}
