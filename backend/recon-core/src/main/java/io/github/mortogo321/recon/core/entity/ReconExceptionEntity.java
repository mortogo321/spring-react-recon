package io.github.mortogo321.recon.core.entity;

import java.time.Instant;
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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * A single break an operator has to work. The natural key
 * {@code (run, merchant, external_ref, status)} is unique so that a restarted or re-run job
 * converges on the same rows instead of duplicating the queue — the batch writer relies on this.
 */
@Entity
@Table(
        name = "recon_exception",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_recon_exception_natural",
                        columnNames = {"run_id", "merchant_id", "external_ref", "status"}),
        indexes = {
            @Index(name = "ix_recon_exception_run_state", columnList = "run_id,state"),
            @Index(name = "ix_recon_exception_severity", columnList = "severity,exposure_amount"),
            @Index(name = "ix_recon_exception_merchant", columnList = "merchant_id"),
            @Index(name = "ix_recon_exception_assignee", columnList = "assigned_to,state")
        })
public class ReconExceptionEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY on purpose: the exception grid renders thousands of rows and never needs the run row. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_exception_run"))
    private ReconRunEntity run;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExceptionState state = ExceptionState.OPEN;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "settlement_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "settlement_currency", length = 3))
    })
    private MoneyAmount settlementAmount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "ledger_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "ledger_currency", length = 3))
    })
    private MoneyAmount ledgerAmount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "exposure_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "exposure_currency", length = 3))
    })
    private MoneyAmount exposure;

    @Column(nullable = false, length = 512)
    private String detail;

    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    @Column(name = "resolution_note", length = 1024)
    private String resolutionNote;

    @Column(name = "submitted_by", length = 64)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "exception", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ExceptionCommentEntity> comments = new ArrayList<>();

    protected ReconExceptionEntity() {
        // for JPA
    }

    private ReconExceptionEntity(
            String merchantId,
            String externalRef,
            MatchStatus status,
            MatchSeverity severity,
            MoneyAmount settlementAmount,
            MoneyAmount ledgerAmount,
            MoneyAmount exposure,
            String detail) {
        this.merchantId = merchantId;
        this.externalRef = externalRef;
        this.status = status;
        this.severity = severity;
        this.settlementAmount = settlementAmount;
        this.ledgerAmount = ledgerAmount;
        this.exposure = exposure;
        this.detail = detail;
    }

    /**
     * Projects a domain outcome onto the persistence model. The switch is exhaustive over the
     * sealed hierarchy, so a new classification cannot reach production without a mapping.
     */
    public static ReconExceptionEntity from(MatchOutcome outcome) {
        Money settlement = switch (outcome) {
            case MatchOutcome.Matched m -> m.amount();
            case MatchOutcome.ToleranceMatched t -> t.settlement();
            case MatchOutcome.AmountMismatch a -> a.settlement();
            case MatchOutcome.MissingInLedger m -> m.settlement();
            case MatchOutcome.MissingInSettlement ignored -> null;
            case MatchOutcome.DuplicateSettlement d -> d.each();
            case MatchOutcome.CurrencyMismatch c -> c.settlement();
        };
        Money ledger = switch (outcome) {
            case MatchOutcome.Matched m -> m.amount();
            case MatchOutcome.ToleranceMatched t -> t.ledger();
            case MatchOutcome.AmountMismatch a -> a.ledger();
            case MatchOutcome.MissingInLedger ignored -> null;
            case MatchOutcome.MissingInSettlement m -> m.ledger();
            case MatchOutcome.DuplicateSettlement ignored -> null;
            case MatchOutcome.CurrencyMismatch ignored -> null;
        };
        return new ReconExceptionEntity(
                outcome.key().merchantId(),
                outcome.key().externalRef(),
                outcome.status(),
                outcome.severity(),
                MoneyAmount.from(settlement),
                MoneyAmount.from(ledger),
                MoneyAmount.from(outcome.exposure()),
                outcome.detail());
    }

    /**
     * Factory for the batch writer, which inserts breaks directly rather than through the run
     * aggregate: a partition can produce tens of thousands of rows and cascading them through an
     * in-memory collection on the run would defeat JDBC batching.
     */
    public static ReconExceptionEntity from(MatchOutcome outcome, ReconRunEntity run) {
        ReconExceptionEntity entity = from(outcome);
        entity.run = run;
        return entity;
    }

    void attachTo(ReconRunEntity run) {
        this.run = run;
    }

    public void assignTo(String user) {
        this.assignedTo = user;
        if (state == ExceptionState.OPEN) {
            this.state = ExceptionState.INVESTIGATING;
        }
    }

    /** Maker half of maker-checker: proposes a resolution but does not apply it. */
    public void submitForApproval(String maker, String note, Instant at) {
        requireTransition(ExceptionState.PENDING_APPROVAL);
        this.state = ExceptionState.PENDING_APPROVAL;
        this.submittedBy = maker;
        this.submittedAt = at;
        this.resolutionNote = note;
    }

    /** Checker half: the approver must be a different user, enforced by the service. */
    public void decide(ExceptionState decision, String approver, Instant at) {
        requireTransition(decision);
        this.state = decision;
        this.decidedBy = approver;
        this.decidedAt = at;
    }

    public void addComment(ExceptionCommentEntity comment) {
        comments.add(comment);
        comment.attachTo(this);
    }

    private void requireTransition(ExceptionState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(state, next);
        }
    }

    public Long getId() {
        return id;
    }

    public ReconRunEntity getRun() {
        return run;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public MatchSeverity getSeverity() {
        return severity;
    }

    public ExceptionState getState() {
        return state;
    }

    public Money getSettlementAmount() {
        return settlementAmount == null ? null : settlementAmount.toMoney();
    }

    public Money getLedgerAmount() {
        return ledgerAmount == null ? null : ledgerAmount.toMoney();
    }

    public Money getExposure() {
        return exposure == null ? null : exposure.toMoney();
    }

    public String getDetail() {
        return detail;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public List<ExceptionCommentEntity> getComments() {
        return List.copyOf(comments);
    }

    /** Thrown when a caller tries to skip a step in the exception workflow. */
    public static final class IllegalStateTransitionException extends IllegalStateException {
        private final ExceptionState from;
        private final ExceptionState to;

        public IllegalStateTransitionException(ExceptionState from, ExceptionState to) {
            super("Cannot move exception from " + from + " to " + to + "; allowed: " + from.allowedNext());
            this.from = from;
            this.to = to;
        }

        public ExceptionState from() {
            return from;
        }

        public ExceptionState to() {
            return to;
        }
    }
}
