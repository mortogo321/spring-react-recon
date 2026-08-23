package io.github.mortogo321.recon.core.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.core.entity.OutboxEventEntity;
import io.github.mortogo321.recon.core.repository.OutboxEventRepository;

/**
 * Drains the outbox. The sink is pluggable — in this POC it logs, in production it would be a
 * signed webhook or a broker publish — but the important part is the failure handling: a bounded
 * retry with the attempt count on the row, and a terminal DEAD state that stops a poison event
 * from blocking everything queued behind it.
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 100;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxEventRepository outbox;
    private final OutboxSink sink;
    private final Clock clock;

    public OutboxDispatcher(OutboxEventRepository outbox, OutboxSink sink, Clock clock) {
        this.outbox = outbox;
        this.sink = sink;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${recon.outbox.dispatch-interval:PT5S}")
    @Transactional
    public int dispatchPending() {
        List<OutboxEventEntity> claimed = outbox.claimPending(BATCH_SIZE);
        int published = 0;
        for (OutboxEventEntity event : claimed) {
            try {
                sink.publish(event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getPayload());
                event.markPublished(Instant.now(clock));
                published++;
            } catch (RuntimeException e) {
                event.markFailed(e.getMessage());
                log.warn(
                        "Outbox dispatch failed for {} (attempt {}): {}",
                        event.getEventType(),
                        event.getAttempts(),
                        e.getMessage());
            }
        }
        return published;
    }

    @Scheduled(cron = "${recon.outbox.purge-cron:0 15 3 * * *}")
    @Transactional
    public int purgeOldPublished() {
        int deleted = outbox.purgePublishedBefore(Instant.now(clock).minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} published outbox events older than {}", deleted, RETENTION);
        }
        return deleted;
    }

    /** Where drained events go. Swapped for a webhook client or broker producer in production. */
    public interface OutboxSink {
        void publish(String eventType, String aggregateType, String aggregateId, String payload);
    }
}
