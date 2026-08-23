package io.github.mortogo321.recon.batch.job;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.core.entity.RunStatus;
import io.github.mortogo321.recon.core.service.ReconRunService;

/**
 * Guards against re-reconciling a date that already completed cleanly.
 *
 * <p>This is the cheap defence against the most common operational mistake: someone re-triggers
 * yesterday because a dashboard looked odd, and the job spends an hour re-deriving an identical
 * answer. A run that finished with breaks is <em>not</em> skipped — those are exactly the ones an
 * operator legitimately wants to re-run after fixing the ledger.
 */
public class AlreadyReconciledDecider implements JobExecutionDecider {

    public static final FlowExecutionStatus SKIPPED = new FlowExecutionStatus("SKIPPED");

    private static final Logger log = LoggerFactory.getLogger(AlreadyReconciledDecider.class);

    private final ReconRunService runService;
    private final boolean enabled;

    public AlreadyReconciledDecider(ReconRunService runService, boolean enabled) {
        this.runService = runService;
        this.enabled = enabled;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        if (!enabled) {
            return FlowExecutionStatus.COMPLETED;
        }
        LocalDate businessDate = jobExecution.getJobParameters().getLocalDate(BatchKeys.PARAM_BUSINESS_DATE);
        String profile = jobExecution.getJobParameters().getString(BatchKeys.PARAM_TOLERANCE_PROFILE);

        boolean alreadyClean = runService.findByKey(businessDate, profile)
                .map(run -> run.getStatus() == RunStatus.COMPLETED)
                .orElse(false);

        if (alreadyClean) {
            log.info("{} already reconciled cleanly with profile '{}'; skipping", businessDate, profile);
            jobExecution.setExitStatus(new ExitStatus(SKIPPED.getName(), "Business date already reconciled cleanly"));
            return SKIPPED;
        }
        return FlowExecutionStatus.COMPLETED;
    }
}
