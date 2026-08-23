package io.github.mortogo321.recon.batch.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Service;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.core.service.ToleranceProfileRegistry;

/**
 * Operational surface over the job: launch, stop, restart, abandon, recover.
 *
 * <p>Wrapped rather than exposing {@link JobOperator} to the web layer for two reasons. First, the
 * job-parameter contract (business date plus tolerance profile, and nothing else) is what defines a
 * job <em>instance</em> — get it wrong and either every launch collides with yesterday's, or every
 * launch creates a new instance and restart becomes impossible. Second, Spring Batch's operational
 * exceptions are checked and framework-specific; translating them here keeps the controller honest
 * about what an operator is actually allowed to do.
 */
@Service
public class ReconJobOperations {

    private static final Logger log = LoggerFactory.getLogger(ReconJobOperations.class);

    /** A sanity bound on the attempt search, not a business limit; nothing should approach it. */
    private static final long MAX_ATTEMPTS = 1_000;

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job reconciliationJob;
    private final ToleranceProfileRegistry tolerances;

    public ReconJobOperations(
            JobOperator jobOperator,
            JobRepository jobRepository,
            Job reconciliationJob,
            ToleranceProfileRegistry tolerances) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.reconciliationJob = reconciliationJob;
        this.tolerances = tolerances;
    }

    /**
     * Starts a reconciliation. Validating the profile up front turns a typo into an immediate 400
     * instead of a job that fails three steps in with a stack trace in the batch metadata.
     */
    public JobHandle launch(LocalDate businessDate, String toleranceProfile) {
        String profile = tolerances.effectiveProfile(toleranceProfile);
        tolerances.resolve(profile);

        JobParameters parameters = nextAttemptParameters(businessDate, profile);

        log.info(
                "Launching {} for {} with profile '{}' (attempt {})",
                BatchKeys.JOB_NAME,
                businessDate,
                profile,
                parameters.getLong(BatchKeys.PARAM_ATTEMPT));
        try {
            return JobHandle.of(jobOperator.start(reconciliationJob, parameters));
        } catch (Exception e) {
            throw new JobOperationException("launch", e);
        }
    }

    /**
     * Chooses the job parameters for this launch, which is the same question as "is this a re-run or
     * a resume?".
     *
     * <p>Spring Batch identifies a job instance by its identifying parameters and refuses to start
     * one that already completed. Business date and profile alone would therefore make reconciling a
     * date a once-ever event — but re-running a date after the ledger has been corrected is routine
     * operational work, not an anomaly. The attempt number gives each deliberate re-run its own
     * instance, while an attempt that failed or was stopped keeps its number, so starting it again
     * resumes from its restart point instead of redoing the work.
     *
     * <p>An attempt that is still running also keeps its number on purpose: the start below then
     * fails as already-running, which is the honest answer to a second click.
     */
    private JobParameters nextAttemptParameters(LocalDate businessDate, String profile) {
        for (long attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            JobParameters candidate = new JobParametersBuilder()
                    .addLocalDate(BatchKeys.PARAM_BUSINESS_DATE, businessDate)
                    .addString(BatchKeys.PARAM_TOLERANCE_PROFILE, profile)
                    .addLong(BatchKeys.PARAM_ATTEMPT, attempt)
                    .toJobParameters();
            JobExecution last = jobRepository.getLastJobExecution(BatchKeys.JOB_NAME, candidate);
            boolean finished = last != null
                    && (last.getStatus() == BatchStatus.COMPLETED || last.getStatus() == BatchStatus.ABANDONED);
            if (!finished) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Refusing to launch: " + businessDate + " with profile '" + profile + "' has already been run "
                        + MAX_ATTEMPTS + " times");
    }

    /** Requests a graceful stop; Spring Batch 6 propagates the signal into running steps. */
    public boolean stop(long executionId) {
        try {
            return jobOperator.stop(execution(executionId, "stop"));
        } catch (Exception e) {
            throw new JobOperationException("stop", e);
        }
    }

    /** Restarts a FAILED or STOPPED execution from its last committed restart point. */
    public Long restart(long executionId) {
        try {
            return jobOperator.restart(execution(executionId, "restart")).getId();
        } catch (Exception e) {
            throw new JobOperationException("restart", e);
        }
    }

    /** Marks an execution abandoned so a new instance can be started for the same date. */
    public JobHandle abandon(long executionId) {
        try {
            return JobHandle.of(jobOperator.abandon(execution(executionId, "abandon")));
        } catch (Exception e) {
            throw new JobOperationException("abandon", e);
        }
    }

    /**
     * Recovers an execution left in a RUNNING state by an abrupt JVM death. Before Spring Batch 6
     * this needed a manual UPDATE against the metadata tables — one of the least pleasant things to
     * do at 3am under an SLA.
     */
    public JobHandle recover(long executionId) {
        return JobHandle.of(jobOperator.recover(execution(executionId, "recover")));
    }

    /** The set of executions Spring Batch still believes are running, for the operations view. */
    public Set<Long> runningExecutions() {
        return jobRepository.findRunningJobExecutions(BatchKeys.JOB_NAME).stream()
                .map(JobExecution::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Per-step progress as data. JobOperator's own summaries are formatted strings, which is fine
     * for a log line and useless to a console that wants to sort on a skip count; the partitioned
     * step is exactly where an operator needs to see which shard read what.
     */
    public List<StepHandle> stepDetails(long executionId) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            return List.of();
        }
        return execution.getStepExecutions().stream()
                .map(StepHandle::of)
                .sorted(Comparator.comparingLong(StepHandle::stepExecutionId))
                .toList();
    }

    /** Every attempt made against one job instance, newest first: the run's restart history. */
    public List<Long> executionsOf(long instanceId) {
        JobInstance instance = jobRepository.getJobInstance(instanceId);
        if (instance == null) {
            return List.of();
        }
        return jobRepository.getJobExecutions(instance).stream()
                .map(JobExecution::getId)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /** JobRepository extends JobExplorer in Spring Batch 6, so there is no separate explorer bean. */
    public Optional<JobHandle> findExecution(long executionId) {
        return Optional.ofNullable(jobRepository.getJobExecution(executionId)).map(JobHandle::of);
    }

    private JobExecution execution(long executionId, String operation) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            throw new JobOperationException(
                    operation, new IllegalArgumentException("No job execution " + executionId));
        }
        return execution;
    }

    /**
     * Framework-free view of a job execution. Spring Batch types stop at this module's boundary so
     * the web layer cannot start depending on batch internals — an ArchUnit rule enforces it.
     */
    public record JobHandle(long executionId, String status, String exitCode, java.time.LocalDateTime startTime) {
        static JobHandle of(JobExecution execution) {
            return new JobHandle(
                    execution.getId(),
                    execution.getStatus().name(),
                    execution.getExitStatus() == null ? null : execution.getExitStatus().getExitCode(),
                    execution.getStartTime());
        }
    }

    /**
     * Framework-free view of one step execution, including the partition steps. Read minus filter
     * minus skip is what actually reached the writer, so all three travel together.
     */
    public record StepHandle(
            long stepExecutionId,
            String name,
            String status,
            String exitCode,
            long readCount,
            long writeCount,
            long filterCount,
            long skipCount,
            long commitCount,
            long rollbackCount,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime) {
        static StepHandle of(StepExecution step) {
            return new StepHandle(
                    step.getId(),
                    step.getStepName(),
                    step.getStatus().name(),
                    step.getExitStatus() == null ? null : step.getExitStatus().getExitCode(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getFilterCount(),
                    step.getSkipCount(),
                    step.getCommitCount(),
                    step.getRollbackCount(),
                    step.getStartTime(),
                    step.getEndTime());
        }
    }

    /** Unchecked translation of Spring Batch's checked operational exceptions. */
    public static final class JobOperationException extends RuntimeException {
        private final String operation;

        public JobOperationException(String operation, Throwable cause) {
            super("Batch " + operation + " failed: " + cause.getMessage(), cause);
            this.operation = operation;
        }

        public String operation() {
            return operation;
        }
    }
}
