package io.github.mortogo321.recon.batch.listener;

import java.util.Currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

import io.github.mortogo321.recon.batch.reader.MerchantReconciliationReader;
import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.batch.support.SummaryAccumulator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Per-partition bookkeeping: seeds the row counters the reader discovered, emits Micrometer
 * metrics, and flags a volume anomaly.
 *
 * <p>The volume check matters more than it looks. A settlement feed that arrives half-empty
 * reconciles beautifully — almost everything matches, because almost nothing was compared. Without
 * an expected-versus-actual assertion that failure mode looks like a great day.
 */
public class ReconStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ReconStepListener.class);

    private final MerchantReconciliationReader reader;
    private final MeterRegistry meters;
    private final Currency reportingCurrency;

    public ReconStepListener(
            MerchantReconciliationReader reader, MeterRegistry meters, Currency reportingCurrency) {
        this.reader = reader;
        this.meters = meters;
        this.reportingCurrency = reportingCurrency;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        var context = stepExecution.getExecutionContext();
        String merchantId = context.getString(BatchKeys.CTX_MERCHANT_ID, "unknown");
        long expected = context.getLong(BatchKeys.CTX_EXPECTED_ROWS, -1);
        long actual = reader.settlementRowsRead();

        new SummaryAccumulator(context, reportingCurrency)
                .addRows(actual, reader.ledgerRowsRead(), reader.excludedRowsRead());

        Tags tags = Tags.of("merchant", merchantId);
        meters.counter("recon.rows.settlement", tags).increment(actual);
        meters.counter("recon.rows.ledger", tags).increment(reader.ledgerRowsRead());
        meters.counter("recon.keys.processed", tags).increment(stepExecution.getReadCount());
        meters.counter("recon.rows.skipped", tags).increment(stepExecution.getSkipCount());

        if (expected >= 0 && expected != actual) {
            log.warn(
                    "Volume mismatch for merchant {}: partitioner expected {} settlement rows, reader saw {}",
                    merchantId,
                    expected,
                    actual);
            meters.counter("recon.volume.mismatch", tags).increment();
            return stepExecution.getExitStatus().addExitDescription(
                    "volume mismatch: expected " + expected + " got " + actual);
        }
        return stepExecution.getExitStatus();
    }
}
