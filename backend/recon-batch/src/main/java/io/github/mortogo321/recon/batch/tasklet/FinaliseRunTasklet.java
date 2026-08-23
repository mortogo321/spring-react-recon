package io.github.mortogo321.recon.batch.tasklet;

import java.util.Currency;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.batch.support.SummaryAccumulator;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.domain.match.ReconciliationSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Last step: fold every partition's counters into one summary and close the run.
 *
 * <p>With local partitioning all worker step executions belong to the same job execution, so their
 * execution contexts are the authoritative per-partition totals — including for partitions that
 * completed in an earlier, failed attempt of a restarted job. Summing them here rather than keeping
 * a running total in memory is what makes the final numbers correct across a restart.
 */
public class FinaliseRunTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FinaliseRunTasklet.class);

    private final ReconRunService runService;
    private final MeterRegistry meters;
    private final Currency reportingCurrency;

    public FinaliseRunTasklet(ReconRunService runService, MeterRegistry meters, Currency reportingCurrency) {
        this.runService = runService;
        this.meters = meters;
        this.reportingCurrency = reportingCurrency;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
        Long runId = jobExecution.getExecutionContext().getLong(BatchKeys.CTX_RUN_ID);

        List<ExecutionContext> partitionContexts = jobExecution.getStepExecutions().stream()
                .filter(step -> step.getStepName().startsWith(BatchKeys.WORKER_STEP))
                .map(step -> step.getExecutionContext())
                .toList();

        ReconciliationSummary summary = SummaryAccumulator.merge(partitionContexts, reportingCurrency);
        runService.complete(runId, summary);

        meters.gauge("recon.run.match_rate", summary.matchRatePercent());
        meters.counter("recon.run.exceptions").increment(summary.exceptionKeys());
        meters.counter("recon.run.completed").increment();

        log.info(
                "Run {} finalised from {} partitions: {}% matched, {} exceptions, exposure {}",
                runId,
                partitionContexts.size(),
                summary.matchRatePercent(),
                summary.exceptionKeys(),
                summary.exposure());
        contribution.incrementWriteCount(1);
        return RepeatStatus.FINISHED;
    }
}
