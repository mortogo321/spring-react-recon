package io.github.mortogo321.recon.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.mortogo321.recon.core.service.OutboxDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Where drained outbox events go in this POC: a structured log line plus a counter.
 *
 * <p>The seam is the point. Swapping this for a signed webhook client, a Kafka producer or an SNS
 * publish is one bean, because the dispatcher only ever knew about {@link OutboxDispatcher.OutboxSink}.
 */
@Configuration
public class OutboxSinkConfig {

    private static final Logger log = LoggerFactory.getLogger("recon.outbox");

    @Bean
    @ConditionalOnMissingBean(OutboxDispatcher.OutboxSink.class)
    public OutboxDispatcher.OutboxSink loggingOutboxSink(MeterRegistry meters) {
        return (eventType, aggregateType, aggregateId, payload) -> {
            meters.counter("recon.outbox.published", Tags.of("event", eventType)).increment();
            log.info("event={} aggregate={}#{} payload={}", eventType, aggregateType, aggregateId, payload);
        };
    }
}
