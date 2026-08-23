package io.github.mortogo321.recon.core.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.core.entity.OutboxEventEntity;
import io.github.mortogo321.recon.core.event.ReconEvents;
import io.github.mortogo321.recon.core.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Records an event in the same transaction as the state change that caused it, then also publishes
 * it in-process. The database row is the durable contract; the in-process event is a convenience
 * for local listeners and is explicitly allowed to be lost on a crash.
 */
@Service
public class OutboxWriter {

    private final OutboxEventRepository outbox;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(
            OutboxEventRepository outbox,
            ApplicationEventPublisher publisher,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, ReconEvents event) {
        Instant now = Instant.now(clock);
        outbox.save(new OutboxEventEntity(
                aggregateType, event.aggregateId(), event.eventType(), serialise(event), now));
        publisher.publishEvent(event);
    }

    private String serialise(ReconEvents event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            // Failing loudly beats writing an unreadable payload that only breaks at dispatch time.
            throw new IllegalStateException("Unable to serialise outbox payload for " + event.eventType(), e);
        }
    }
}
