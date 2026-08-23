package io.github.mortogo321.recon.core.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Application events published after commit, and mirrored into the outbox for downstream systems. */
public sealed interface ReconEvents {

    String aggregateId();

    String eventType();

    record RunCompleted(
            Long runId,
            LocalDate businessDate,
            long matchedKeys,
            long exceptionKeys,
            BigDecimal matchRate,
            BigDecimal exposure,
            String currency,
            Instant at)
            implements ReconEvents {

        @Override
        public String aggregateId() {
            return String.valueOf(runId);
        }

        @Override
        public String eventType() {
            return "recon.run.completed";
        }
    }

    record RunFailed(Long runId, LocalDate businessDate, String reason, Instant at) implements ReconEvents {

        @Override
        public String aggregateId() {
            return String.valueOf(runId);
        }

        @Override
        public String eventType() {
            return "recon.run.failed";
        }
    }

    record ExceptionDecided(
            Long exceptionId, Long runId, String merchantId, String externalRef, String decision, String decidedBy,
            Instant at)
            implements ReconEvents {

        @Override
        public String aggregateId() {
            return String.valueOf(exceptionId);
        }

        @Override
        public String eventType() {
            return "recon.exception.decided";
        }
    }

    record CriticalExposureBreached(
            Long runId, LocalDate businessDate, BigDecimal exposure, BigDecimal threshold, String currency, Instant at)
            implements ReconEvents {

        @Override
        public String aggregateId() {
            return String.valueOf(runId);
        }

        @Override
        public String eventType() {
            return "recon.exposure.breached";
        }
    }
}
