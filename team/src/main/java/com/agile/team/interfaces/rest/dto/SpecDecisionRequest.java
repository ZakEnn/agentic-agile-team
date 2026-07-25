package com.agile.team.interfaces.rest.dto;

import java.util.List;

/**
 * A human decision at the SPEC_APPROVAL gate.
 *
 * @param decidedBy who is deciding. Required — an unattributed approval is not an
 *                  audit trail, and distinguishing human from automated decisions
 *                  is what keeps trust metrics meaningful.
 * @param reason    rationale. Required on rejection.
 */
public record SpecDecisionRequest(String decidedBy, String reason) {
    public SpecDecisionRequest {
        if (decidedBy == null || decidedBy.isBlank()) {
            throw new IllegalArgumentException("decidedBy must not be blank");
        }
    }

    /** Payload for editing a draft before deciding on it. */
    public record Edit(
            String editedBy,
            String summary,
            String description,
            List<String> acceptanceCriteria,
            List<String> outOfScope
    ) {
        public Edit {
            if (editedBy == null || editedBy.isBlank()) {
                throw new IllegalArgumentException("editedBy must not be blank");
            }
        }
    }
}
