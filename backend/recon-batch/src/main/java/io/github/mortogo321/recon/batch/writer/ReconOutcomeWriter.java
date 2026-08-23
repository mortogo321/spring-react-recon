package io.github.mortogo321.recon.batch.writer;

import java.util.Currency;
import java.util.List;

import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import io.github.mortogo321.recon.batch.support.SummaryAccumulator;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.domain.match.MatchOutcome;

/**
 * Persists a chunk's breaks and folds its counters into the partition's execution context.
 *
 * <p>The write is idempotent at the row level ({@code ReconRunService} skips breaks that already
 * exist for the run), which is what makes a mid-chunk failure safe to retry: the re-executed chunk
 * converges on the same rows instead of duplicating the operator's queue.
 */
public class ReconOutcomeWriter implements ItemWriter<List<MatchOutcome>> {

    private final ReconRunService runService;
    private final Long runId;
    private final Currency reportingCurrency;

    public ReconOutcomeWriter(ReconRunService runService, Long runId, Currency reportingCurrency) {
        this.runService = runService;
        this.runId = runId;
        this.reportingCurrency = reportingCurrency;
    }

    @Override
    public void write(Chunk<? extends List<MatchOutcome>> chunk) {
        List<MatchOutcome> flattened = chunk.getItems().stream().flatMap(List::stream).toList();
        if (flattened.isEmpty()) {
            return;
        }
        runService.recordOutcomes(runId, flattened);
        accumulate(flattened);
    }

    /**
     * Counters live in the step execution context rather than in a field so they survive a restart
     * and can be summed across partitions when the job finalises.
     */
    private void accumulate(List<MatchOutcome> outcomes) {
        var stepContext = StepSynchronizationManager.getContext();
        if (stepContext == null) {
            return;
        }
        SummaryAccumulator accumulator =
                new SummaryAccumulator(stepContext.getStepExecution().getExecutionContext(), reportingCurrency);
        outcomes.forEach(accumulator::add);
    }
}
