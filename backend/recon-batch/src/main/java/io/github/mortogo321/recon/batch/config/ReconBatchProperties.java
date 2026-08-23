package io.github.mortogo321.recon.batch.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tuning knobs for the reconciliation job. All have defaults sized for the demo dataset. */
@Validated
@ConfigurationProperties(prefix = "recon.batch")
public record ReconBatchProperties(
        @Positive int chunkSize,
        @Positive int gridSize,
        @Positive int workerConcurrency,
        @Positive int readerPageSize,
        @Min(0) long skipLimit,
        @Min(0) long retryLimit,
        Duration retryInitialDelay,
        Duration retryMaxDelay,
        String reportingCurrency,
        boolean skipCompletedRuns) {

    public ReconBatchProperties {
        chunkSize = chunkSize <= 0 ? 500 : chunkSize;
        gridSize = gridSize <= 0 ? 8 : gridSize;
        workerConcurrency = workerConcurrency <= 0 ? 4 : workerConcurrency;
        readerPageSize = readerPageSize <= 0 ? 2000 : readerPageSize;
        skipLimit = skipLimit < 0 ? 100 : skipLimit;
        retryLimit = retryLimit < 0 ? 3 : retryLimit;
        retryInitialDelay = retryInitialDelay == null ? Duration.ofMillis(250) : retryInitialDelay;
        retryMaxDelay = retryMaxDelay == null ? Duration.ofSeconds(5) : retryMaxDelay;
        reportingCurrency = reportingCurrency == null || reportingCurrency.isBlank() ? "THB" : reportingCurrency;
    }
}
