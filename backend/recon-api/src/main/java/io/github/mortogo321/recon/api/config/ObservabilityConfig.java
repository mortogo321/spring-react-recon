package io.github.mortogo321.recon.api.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.mortogo321.recon.core.entity.OutboxEventEntity;
import io.github.mortogo321.recon.core.repository.OutboxEventRepository;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Application-specific observability.
 *
 * <p>The outbox health indicator is the one that earns its keep: events piling up in DEAD state
 * means downstream systems have silently stopped hearing about reconciliation results, and that is
 * invisible in every other signal — the API is up, the job is green, and nobody knows.
 */
@Configuration
public class ObservabilityConfig {

    private static final long DEAD_EVENT_ALERT_THRESHOLD = 1;
    private static final long PENDING_BACKLOG_THRESHOLD = 1_000;

    /** Enables {@code @Timed} on any method that wants a timer without hand-rolling one. */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public HealthIndicator outboxHealthIndicator(OutboxEventRepository outbox, MeterRegistry meters) {
        meters.gauge(
                "recon.outbox.pending",
                outbox,
                repo -> repo.countByStatus(OutboxEventEntity.Status.PENDING));
        meters.gauge("recon.outbox.dead", outbox, repo -> repo.countByStatus(OutboxEventEntity.Status.DEAD));

        return () -> {
            long dead = outbox.countByStatus(OutboxEventEntity.Status.DEAD);
            long pending = outbox.countByStatus(OutboxEventEntity.Status.PENDING);
            Health.Builder builder = dead >= DEAD_EVENT_ALERT_THRESHOLD || pending > PENDING_BACKLOG_THRESHOLD
                    ? Health.down()
                    : Health.up();
            return builder.withDetail("pending", pending).withDetail("dead", dead).build();
        };
    }
}
