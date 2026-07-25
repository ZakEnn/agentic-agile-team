package com.agile.team.domain.specification;

import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A specification under the SPEC_APPROVAL gate.
 * <p>
 * Extended in M1 with a {@link SpecificationStatus} lifecycle and human-decision
 * metadata. Previously a Specification was a write-once bag of text with no notion
 * of whether anyone had agreed to it, which is why the pipeline had to be disabled
 * by commenting out code rather than paused by a gate.
 */
public class Specification {

    private final SpecificationId id;
    private final String sourcePageId;
    private final Instant createdAt;

    // Mutable because a human may edit a draft at the gate before approving it.
    private String title;
    private String content;
    private List<String> acceptanceCriteria;
    private List<String> outOfScope;

    private SpecificationStatus status;
    private String decidedBy;
    private String decisionReason;
    private Instant decidedAt;

    public Specification(SpecificationId id, String title, String content, String sourcePageId,
                         List<String> acceptanceCriteria) {
        if (id == null) throw new IllegalArgumentException("Specification id must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title must not be blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Content must not be blank");
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePageId = sourcePageId;
        this.acceptanceCriteria = acceptanceCriteria != null ? new ArrayList<>(acceptanceCriteria) : new ArrayList<>();
        this.outOfScope = new ArrayList<>();
        this.createdAt = Instant.now();
        this.status = SpecificationStatus.DRAFT;
    }

    /** Reconstruction constructor for loading from persistence. */
    public Specification(SpecificationId id, String title, String content, String sourcePageId,
                         List<String> acceptanceCriteria, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePageId = sourcePageId;
        this.acceptanceCriteria = acceptanceCriteria != null ? new ArrayList<>(acceptanceCriteria) : new ArrayList<>();
        this.outOfScope = new ArrayList<>();
        this.createdAt = createdAt;
        this.status = SpecificationStatus.DRAFT;
    }

    /** Full reconstruction constructor including gate state. */
    public Specification(SpecificationId id, String title, String content, String sourcePageId,
                         List<String> acceptanceCriteria, List<String> outOfScope, Instant createdAt,
                         SpecificationStatus status, String decidedBy, String decisionReason, Instant decidedAt) {
        this(id, title, content, sourcePageId, acceptanceCriteria, createdAt);
        this.outOfScope = outOfScope != null ? new ArrayList<>(outOfScope) : new ArrayList<>();
        this.status = status != null ? status : SpecificationStatus.DRAFT;
        this.decidedBy = decidedBy;
        this.decisionReason = decisionReason;
        this.decidedAt = decidedAt;
    }

    /**
     * Build a specification from a validated Spec Agent artifact.
     * <p>
     * The acceptance criteria carried here are the same list the QA agent will later
     * verify against — the point of validating {@link SpecDraft} at construction is
     * that they can never be silently empty.
     */
    public static Specification fromDraft(SpecificationId id, SpecDraft draft, String sourcePageId) {
        Specification spec = new Specification(id, draft.summary(), draft.description(),
                sourcePageId, draft.acceptanceCriteria());
        spec.outOfScope = new ArrayList<>(draft.outOfScope());
        return spec;
    }

    /** The current state as a draft artifact — used for editing and for downstream agents. */
    public SpecDraft toDraft() {
        return new SpecDraft(title, content, acceptanceCriteria, outOfScope);
    }

    /**
     * Apply a human edit at the gate. Only permitted while the spec is still a draft:
     * editing an already-approved spec would invalidate the approval it carries.
     */
    public void applyEdit(SpecDraft edited, String editedBy) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot edit a specification that is already " + status
                            + " — its decision record would no longer describe what was decided");
        }
        if (edited == null) throw new IllegalArgumentException("edited draft must not be null");
        if (editedBy == null || editedBy.isBlank()) {
            throw new IllegalArgumentException("editedBy must not be blank");
        }
        this.title = edited.summary();
        this.content = edited.description();
        this.acceptanceCriteria = new ArrayList<>(edited.acceptanceCriteria());
        this.outOfScope = new ArrayList<>(edited.outOfScope());
    }

    /** Record a decision from the SPEC_APPROVAL gate. */
    public void applyDecision(GateDecision decision) {
        if (decision == null) throw new IllegalArgumentException("decision must not be null");
        if (decision.gate() != GateName.SPEC_APPROVAL) {
            throw new IllegalArgumentException("Expected SPEC_APPROVAL decision but got " + decision.gate());
        }
        if (status.isTerminal()) {
            throw new IllegalStateException("Specification already " + status + "; cannot decide twice");
        }
        // A spec with no acceptance criteria cannot be approved: the QA agent would
        // have nothing to verify, so the wave could reach review with no definition
        // of done. Construction stays permissive (persistence reconstruction and
        // legacy rows), but approval is where the invariant is enforced.
        if (decision.approved() && acceptanceCriteria.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot approve a specification with no acceptance criteria — "
                            + "there would be nothing for the QA agent to verify");
        }
        this.status = decision.approved() ? SpecificationStatus.APPROVED : SpecificationStatus.REJECTED;
        this.decidedBy = decision.decidedBy();
        this.decisionReason = decision.reason();
        this.decidedAt = decision.decidedAt();
    }

    public SpecificationId getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourcePageId() { return sourcePageId; }
    public List<String> getAcceptanceCriteria() { return Collections.unmodifiableList(acceptanceCriteria); }
    public List<String> getOutOfScope() { return Collections.unmodifiableList(outOfScope); }
    public Instant getCreatedAt() { return createdAt; }
    public SpecificationStatus getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getDecidedAt() { return decidedAt; }
    public boolean isApproved() { return status.isApproved(); }
}
