package io.github.mortogo321.recon.core.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.github.mortogo321.recon.domain.match.ReconciliationSummary;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * One reconciliation of one business date. {@code runKey} is the idempotency anchor: re-triggering
 * the same business date with the same tolerance profile finds this row instead of creating a
 * second, competing run.
 */
@Entity
@Table(
        name = "recon_run",
        uniqueConstraints = @UniqueConstraint(name = "uk_recon_run_key", columnNames = "run_key"),
        indexes = {
            @Index(name = "ix_recon_run_business_date", columnList = "business_date"),
            @Index(name = "ix_recon_run_status", columnList = "status")
        })
public class ReconRunEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, length = 100)
    private String runKey;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "tolerance_profile", nullable = false, length = 64)
    private String toleranceProfile;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "settlement_rows", nullable = false)
    private long settlementRows;

    @Column(name = "ledger_rows", nullable = false)
    private long ledgerRows;

    @Column(name = "excluded_rows", nullable = false)
    private long excludedRows;

    @Column(name = "matched_keys", nullable = false)
    private long matchedKeys;

    @Column(name = "exception_keys", nullable = false)
    private long exceptionKeys;

    @Column(name = "match_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchRate = BigDecimal.ZERO;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "matched_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "matched_currency", length = 3))
    })
    private MoneyAmount matchedAmount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "exposure_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "exposure_currency", length = 3))
    })
    private MoneyAmount exposure;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    /**
     * Cascade is intentional and one-directional: deleting a run disposes of its exceptions, and
     * nothing else in the model references them. Fetching is always explicit via the repository.
     */
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReconExceptionEntity> exceptions = new ArrayList<>();

    protected ReconRunEntity() {
        // for JPA
    }

    public ReconRunEntity(String runKey, LocalDate businessDate, String toleranceProfile, String triggeredBy) {
        this.runKey = runKey;
        this.businessDate = businessDate;
        this.toleranceProfile = toleranceProfile;
        this.triggeredBy = triggeredBy;
    }

    public void markRunning(long jobExecutionId, Instant at) {
        this.jobExecutionId = jobExecutionId;
        this.status = RunStatus.RUNNING;
        this.startedAt = at;
        this.failureReason = null;
    }

    /** Applies a domain summary and derives the terminal status from whether breaks were found. */
    public void complete(ReconciliationSummary summary, Instant at) {
        this.settlementRows = summary.settlementRows();
        this.ledgerRows = summary.ledgerRows();
        this.excludedRows = summary.excludedRows();
        this.matchedKeys = summary.matchedKeys();
        this.exceptionKeys = summary.exceptionKeys();
        this.matchRate = summary.matchRatePercent();
        this.matchedAmount = MoneyAmount.from(summary.matchedAmount());
        this.exposure = MoneyAmount.from(summary.exposure());
        this.finishedAt = at;
        this.status = summary.exceptionKeys() == 0 ? RunStatus.COMPLETED : RunStatus.COMPLETED_WITH_BREAKS;
    }

    public void fail(String reason, Instant at) {
        this.status = RunStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1024));
        this.finishedAt = at;
    }

    public void markStopping() {
        this.status = RunStatus.STOPPING;
    }

    public void markStopped(Instant at) {
        this.status = RunStatus.STOPPED;
        this.finishedAt = at;
    }

    public void addException(ReconExceptionEntity exception) {
        exceptions.add(exception);
        exception.attachTo(this);
    }

    public Long getId() {
        return id;
    }

    public String getRunKey() {
        return runKey;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public String getToleranceProfile() {
        return toleranceProfile;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getSettlementRows() {
        return settlementRows;
    }

    public long getLedgerRows() {
        return ledgerRows;
    }

    public long getExcludedRows() {
        return excludedRows;
    }

    public long getMatchedKeys() {
        return matchedKeys;
    }

    public long getExceptionKeys() {
        return exceptionKeys;
    }

    public BigDecimal getMatchRate() {
        return matchRate;
    }

    public Money getMatchedAmount() {
        return matchedAmount == null ? null : matchedAmount.toMoney();
    }

    public Money getExposure() {
        return exposure == null ? null : exposure.toMoney();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<ReconExceptionEntity> getExceptions() {
        return List.copyOf(exceptions);
    }
}
