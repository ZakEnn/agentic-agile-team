package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
public class StateTransitionEmbeddable {

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "authorized_by")
    private String authorizedBy;

    @Column(name = "transitioned_at")
    private Instant transitionedAt;

    public StateTransitionEmbeddable() {}

    public StateTransitionEmbeddable(String fromStatus, String toStatus, String authorizedBy, Instant transitionedAt) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.authorizedBy = authorizedBy;
        this.transitionedAt = transitionedAt;
    }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getAuthorizedBy() { return authorizedBy; }
    public void setAuthorizedBy(String authorizedBy) { this.authorizedBy = authorizedBy; }
    public Instant getTransitionedAt() { return transitionedAt; }
    public void setTransitionedAt(Instant transitionedAt) { this.transitionedAt = transitionedAt; }
}
