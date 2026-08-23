package io.github.mortogo321.recon.core.entity;

import java.util.Set;

/**
 * Operator workflow for a single break. Transitions are enforced in the service layer rather than
 * left to the caller, because a break that skips approval is an audit finding.
 */
public enum ExceptionState {
    OPEN,
    INVESTIGATING,
    PENDING_APPROVAL,
    RESOLVED,
    REJECTED,
    WRITTEN_OFF;

    public Set<ExceptionState> allowedNext() {
        return switch (this) {
            case OPEN -> Set.of(INVESTIGATING, PENDING_APPROVAL);
            case INVESTIGATING -> Set.of(OPEN, PENDING_APPROVAL);
            // Approval is the maker-checker gate: only an approver moves a break out of here.
            case PENDING_APPROVAL -> Set.of(RESOLVED, REJECTED, WRITTEN_OFF);
            case REJECTED -> Set.of(INVESTIGATING);
            case RESOLVED, WRITTEN_OFF -> Set.of();
        };
    }

    public boolean canTransitionTo(ExceptionState next) {
        return allowedNext().contains(next);
    }

    public boolean isClosed() {
        return this == RESOLVED || this == WRITTEN_OFF;
    }
}
