package io.github.mortogo321.recon.core.entity;

/** Lifecycle of a reconciliation run, mirroring the underlying Spring Batch job execution. */
public enum RunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_BREAKS,
    FAILED,
    STOPPING,
    STOPPED,
    ABANDONED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_BREAKS || this == FAILED || this == STOPPED
                || this == ABANDONED;
    }

    public boolean isRestartable() {
        return this == FAILED || this == STOPPED;
    }
}
