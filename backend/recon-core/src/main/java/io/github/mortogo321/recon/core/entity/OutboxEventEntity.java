package io.github.mortogo321.recon.core.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Transactional outbox. Notifying downstream systems (treasury, alerting) is not allowed to
 * happen inside the reconciliation transaction — a slow webhook must never roll back a run, and a
 * committed run must never fail to be announced. Writing the intent to the same database in the
 * same transaction, then draining it separately, is what buys both guarantees without a broker.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = {
            @Index(name = "ix_outbox_dispatch", columnList = "status,occurred_at"),
            @Index(name = "ix_outbox_aggregate", columnList = "aggregate_type,aggregate_id")
        })
public class OutboxEventEntity {

    public enum Status {
        PENDING,
        PUBLISHED,
        DEAD
    }

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected OutboxEventEntity() {
        // for JPA
    }

    public OutboxEventEntity(String aggregateType, String aggregateId, String eventType, String payload, Instant at) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = at;
    }

    public void markPublished(Instant at) {
        this.status = Status.PUBLISHED;
        this.publishedAt = at;
        this.lastError = null;
    }

    /** Retries are bounded; beyond the limit the event lands in a dead state for manual review. */
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1024));
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.DEAD;
        }
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
