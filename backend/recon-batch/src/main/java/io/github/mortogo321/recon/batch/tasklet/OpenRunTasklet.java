package io.github.mortogo321.recon.batch.tasklet;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.service.LedgerQueryService;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.legacy.gateway.LegacySettlementGateway;

/**
 * First step: open (or re-open) the run row and publish its id into the job execution context so
 * every downstream step and partition addresses the same aggregate.
 *
 * <p>Opening the run in a step rather than at launch time is deliberate — it means a restarted job
 * re-attaches to the existing run instead of orphaning it, and the run's lifecycle is visible in
 * the batch metadata alongside everything else.
 */
public class OpenRunTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(OpenRunTasklet.class);

    private final ReconRunService runService;
    private final LegacySettlementGateway legacy;
    private final LedgerQueryService ledger;

    public OpenRunTasklet(ReconRunService runService, LegacySettlementGateway legacy, LedgerQueryService ledger) {
        this.runService = runService;
        this.legacy = legacy;
        this.ledger = ledger;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var stepExecution = chunkContext.getStepContext().getStepExecution();
        var params = stepExecution.getJobParameters();
        LocalDate businessDate = params.getLocalDate(BatchKeys.PARAM_BUSINESS_DATE);
        String profile = params.getString(BatchKeys.PARAM_TOLERANCE_PROFILE);

        ReconRunEntity run = runService.openRun(businessDate, profile);
        runService.markRunning(run.getId(), stepExecution.getJobExecutionId());

        var jobContext = stepExecution.getJobExecution().getExecutionContext();
        jobContext.putLong(BatchKeys.CTX_RUN_ID, run.getId());

        long settlementRows = legacy.countFor(businessDate);
        long ledgerRows = ledger.countFor(businessDate);
        jobContext.putLong(BatchKeys.CTX_EXPECTED_ROWS, settlementRows);

        log.info(
                "Run {} (id {}) opened for {}: {} settlement rows, {} ledger rows, profile '{}'",
                run.getRunKey(),
                run.getId(),
                businessDate,
                settlementRows,
                ledgerRows,
                profile);
        contribution.incrementWriteCount(1);
        return RepeatStatus.FINISHED;
    }
}
