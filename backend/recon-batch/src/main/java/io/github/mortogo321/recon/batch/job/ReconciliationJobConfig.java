package io.github.mortogo321.recon.batch.job;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.LimitCheckingExceptionHierarchySkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.mortogo321.recon.batch.config.ReconBatchProperties;
import io.github.mortogo321.recon.batch.listener.ReconSkipListener;
import io.github.mortogo321.recon.batch.listener.ReconStepListener;
import io.github.mortogo321.recon.batch.partition.MerchantShardPartitioner;
import io.github.mortogo321.recon.batch.processor.ReconciliationProcessor;
import io.github.mortogo321.recon.batch.reader.MerchantReconciliationReader;
import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.batch.support.ReconCandidate;
import io.github.mortogo321.recon.batch.tasklet.FinaliseRunTasklet;
import io.github.mortogo321.recon.batch.tasklet.OpenRunTasklet;
import io.github.mortogo321.recon.batch.writer.ReconOutcomeWriter;
import io.github.mortogo321.recon.core.service.LedgerQueryService;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.core.service.ToleranceProfileRegistry;
import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.ReconciliationEngine;
import io.github.mortogo321.recon.legacy.gateway.LegacySettlementGateway;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The reconciliation job.
 *
 * <pre>
 *   openRun ──▶ alreadyReconciled? ──SKIPPED──▶ (end)
 *                      │
 *                      └──▶ reconcilePartitionedStep ──▶ finaliseRun
 *                             ├─ merchant-0  (chunk-oriented, fault tolerant)
 *                             ├─ merchant-1
 *                             └─ merchant-N
 * </pre>
 *
 * <p>Both the reader and the writer are {@code @StepScope}: they are constructed per partition and
 * take their merchant from the partition's execution context. That is the mechanism that turns one
 * step definition into N independent, individually restartable units of work.
 */
@Configuration
public class ReconciliationJobConfig {

    /**
     * Failures worth retrying are transient infrastructure faults — a connection reclaimed from the
     * pool, a brief Oracle hiccup. Anything else is a data or logic problem that will fail again
     * identically, and retrying it just delays the alert.
     */
    private static final Set<Class<? extends Throwable>> RETRYABLE =
            Set.of(TransientDataAccessException.class, CannotGetJdbcConnectionException.class);

    /**
     * Skippable failures are per-row data faults: a status code the legacy feed has never sent
     * before, a currency that is not ISO. One bad row must not abort a two-hour run, but the skip
     * limit means a systematically broken file still fails loudly instead of quietly matching 3%.
     */
    private static final Set<Class<? extends Throwable>> SKIPPABLE =
            Set.of(IllegalArgumentException.class, NullPointerException.class);

    // ---------------------------------------------------------------- job

    @Bean
    public Job reconciliationJob(
            JobRepository jobRepository,
            Step openRunStep,
            Step reconcilePartitionedStep,
            Step finaliseRunStep,
            AlreadyReconciledDecider alreadyReconciledDecider) {
        return new JobBuilder(BatchKeys.JOB_NAME, jobRepository)
                .flow(openRunStep)
                .next(alreadyReconciledDecider)
                .on(AlreadyReconciledDecider.SKIPPED.getName())
                .end()
                .from(alreadyReconciledDecider)
                .on("*")
                .to(reconcilePartitionedStep)
                .next(finaliseRunStep)
                .build()
                .build();
    }

    @Bean
    public AlreadyReconciledDecider alreadyReconciledDecider(
            ReconRunService runService, ReconBatchProperties properties) {
        return new AlreadyReconciledDecider(runService, properties.skipCompletedRuns());
    }

    // ---------------------------------------------------------------- steps

    @Bean
    public Step openRunStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ReconRunService runService,
            LegacySettlementGateway legacy,
            LedgerQueryService ledger) {
        return new StepBuilder("openRunStep", jobRepository)
                .tasklet(new OpenRunTasklet(runService, legacy, ledger), transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    /**
     * Manager step. {@code gridSize} is only a hint to the partitioner — the real partition count
     * is the number of active merchants — while {@code taskExecutor} bounds how many run at once.
     */
    @Bean
    public Step reconcilePartitionedStep(
            JobRepository jobRepository,
            Step reconcileMerchantStep,
            MerchantShardPartitioner merchantShardPartitioner,
            AsyncTaskExecutor partitionTaskExecutor,
            ReconBatchProperties properties) {
        return new StepBuilder(BatchKeys.MANAGER_STEP, jobRepository)
                .partitioner(BatchKeys.WORKER_STEP, merchantShardPartitioner)
                .step(reconcileMerchantStep)
                .gridSize(properties.gridSize())
                .taskExecutor(partitionTaskExecutor)
                .build();
    }

    /** Worker step: one merchant, chunk-oriented over match keys, fault tolerant. */
    @Bean
    public Step reconcileMerchantStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MerchantReconciliationReader merchantReconciliationReader,
            ReconciliationProcessor reconciliationProcessor,
            ReconOutcomeWriter reconOutcomeWriter,
            ReconStepListener reconStepListener,
            ReconSkipListener reconSkipListener,
            ReconBatchProperties properties) {
        return new StepBuilder(BatchKeys.WORKER_STEP, jobRepository)
                .<ReconCandidate, List<MatchOutcome>>chunk(properties.chunkSize())
                .reader(merchantReconciliationReader)
                .processor(reconciliationProcessor)
                .writer(reconOutcomeWriter)
                .transactionManager(transactionManager)
                .stream(merchantReconciliationReader)
                .faultTolerant()
                .retryPolicy(retryPolicy(properties))
                .skipPolicy(skipPolicy(properties))
                .skipListener(reconSkipListener)
                .listener(reconStepListener)
                .build();
    }

    @Bean
    public Step finaliseRunStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ReconRunService runService,
            MeterRegistry meters,
            ReconBatchProperties properties) {
        return new StepBuilder("finaliseRunStep", jobRepository)
                .tasklet(
                        new FinaliseRunTasklet(
                                runService, meters, Currency.getInstance(properties.reportingCurrency())),
                        transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    // ---------------------------------------------------------------- step-scoped components

    @Bean
    @StepScope
    public MerchantShardPartitioner merchantShardPartitioner(
            LegacySettlementGateway legacy,
            LedgerQueryService ledger,
            @Value("#{jobParameters['" + BatchKeys.PARAM_BUSINESS_DATE + "']}") LocalDate businessDate,
            @Value("#{jobExecutionContext['" + BatchKeys.CTX_RUN_ID + "']}") Long runId) {
        return new MerchantShardPartitioner(legacy, ledger, businessDate, runId);
    }

    @Bean
    @StepScope
    public MerchantReconciliationReader merchantReconciliationReader(
            LegacySettlementGateway legacy,
            LedgerQueryService ledger,
            ReconBatchProperties properties,
            @Value("#{jobParameters['" + BatchKeys.PARAM_BUSINESS_DATE + "']}") LocalDate businessDate,
            @Value("#{stepExecutionContext['" + BatchKeys.CTX_MERCHANT_ID + "']}") String merchantId) {
        return new MerchantReconciliationReader(
                legacy, ledger, businessDate, merchantId, properties.readerPageSize());
    }

    @Bean
    @StepScope
    public ReconciliationProcessor reconciliationProcessor(
            ToleranceProfileRegistry tolerances,
            ReconBatchProperties properties,
            @Value("#{jobParameters['" + BatchKeys.PARAM_TOLERANCE_PROFILE + "']}") String toleranceProfile) {
        return new ReconciliationProcessor(
                ReconciliationEngine.reportingIn(properties.reportingCurrency()), tolerances.resolve(toleranceProfile));
    }

    @Bean
    @StepScope
    public ReconOutcomeWriter reconOutcomeWriter(
            ReconRunService runService,
            ReconBatchProperties properties,
            @Value("#{stepExecutionContext['" + BatchKeys.CTX_RUN_ID + "']}") Long runId) {
        return new ReconOutcomeWriter(runService, runId, Currency.getInstance(properties.reportingCurrency()));
    }

    @Bean
    @StepScope
    public ReconStepListener reconStepListener(
            MerchantReconciliationReader merchantReconciliationReader,
            MeterRegistry meters,
            ReconBatchProperties properties) {
        return new ReconStepListener(
                merchantReconciliationReader, meters, Currency.getInstance(properties.reportingCurrency()));
    }

    @Bean
    public ReconSkipListener reconSkipListener(MeterRegistry meters) {
        return new ReconSkipListener(meters);
    }

    // ---------------------------------------------------------------- policies

    private static RetryPolicy retryPolicy(ReconBatchProperties properties) {
        return RetryPolicy.builder()
                .maxRetries(properties.retryLimit())
                .delay(properties.retryInitialDelay())
                .multiplier(2.0)
                .maxDelay(properties.retryMaxDelay())
                // Jitter stops every partition worker from retrying in lockstep after a blip.
                .jitter(properties.retryInitialDelay().dividedBy(2))
                .includes(RETRYABLE)
                .build();
    }

    private static SkipPolicy skipPolicy(ReconBatchProperties properties) {
        return new LimitCheckingExceptionHierarchySkipPolicy(SKIPPABLE, properties.skipLimit());
    }
}
