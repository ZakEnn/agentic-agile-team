package com.agile.team.application.usecase;

import com.agile.team.application.orchestrator.Orchestrator;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import org.springframework.stereotype.Service;

/**
 * The human side of the SPEC_APPROVAL gate: approve, reject, or edit a draft.
 * <p>
 * SDLC_AGENT_PLAN.md §4 argues this is the highest-value checkpoint in the whole
 * pipeline — MAST attributes the largest share of multi-agent failures to
 * specification and system-design issues, so a bad spec stopped here prevents the
 * entire downstream cascade.
 */
@Service
public class DecideSpecificationUseCase {

    private final Orchestrator orchestrator;

    public DecideSpecificationUseCase(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public Wave approve(WaveId waveId, SpecificationId specificationId, String approvedBy, String reason) {
        return orchestrator.decideSpecification(waveId, specificationId,
                GateDecision.approvedBy(GateName.SPEC_APPROVAL, approvedBy,
                        reason != null && !reason.isBlank() ? reason : "approved"));
    }

    public Wave reject(WaveId waveId, SpecificationId specificationId, String rejectedBy, String reason) {
        return orchestrator.decideSpecification(waveId, specificationId,
                GateDecision.rejectedBy(GateName.SPEC_APPROVAL, rejectedBy, reason));
    }

    public Wave edit(WaveId waveId, SpecificationId specificationId, SpecDraft edited, String editedBy) {
        return orchestrator.editSpecification(waveId, specificationId, edited, editedBy);
    }
}
