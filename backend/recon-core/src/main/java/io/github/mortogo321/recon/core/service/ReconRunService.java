package io.github.mortogo321.recon.core.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.core.config.CurrentActorProvider;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.entity.RunStatus;
import io.github.mortogo321.recon.core.event.ReconEvents;
import io.github.mortogo321.recon.core.repository.ReconExceptionRepository;
import io.github.mortogo321.recon.core.repository.ReconRunRepository;
import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.ReconciliationSummary;

/**
 * Owns the reconciliation run aggregate. Every method here is designed to be safe to call twice:
 * the batch job can be restarted mid-flight, and a restart that duplicated the exception queue
 * would be worse than the original failure.
 */
@Service
public class ReconRunService {

    private static final Logger log = LoggerFactory.getLogger(ReconRunService.class);

    private final ReconRunRepository runs;
    private final ReconExceptionRepository exceptions;
    private final OutboxWriter outbox;
    private final CurrentActorProvider actor;
    private final Clock clock;

    public ReconRunService(
            ReconRunRepository runs,
            ReconExceptionRepository exceptions,
            OutboxWriter outbox,
            CurrentActorProvider actor,
            Clock clock) {
        this.runs = runs;
        this.exceptions = exceptions;
        this.outbox = outbox;
        this.actor = actor;
        this.clock = clock;
    }

    public static String runKey(LocalDate businessDate, String toleranceProfile) {
        return businessDate + ":" + (toleranceProfile == null ? "default" : toleranceProfile);
    }

    /**
     * Finds or creates the run for a business date and profile. The pessimistic read plus the
     * unique constraint on {@code run_key} means a concurrent trigger either waits and sees the
     * existing row, or loses the insert race and is retried into the same outcome.
     */
    @Transactional
    public ReconRunEntity openRun(LocalDate businessDate, String toleranceProfile) {
        String key = runKey(businessDate, toleranceProfile);
        return runs.findByRunKeyForUpdate(key).orElseGet(() -> {
            log.info("Opening reconciliation run {}", key);
            return runs.save(new ReconRunEntity(key, businessDate, toleranceProfile, actor.currentActor()));
        });
    }

    @Transactional
    public void markRunning(Long runId, long jobExecutionId) {
        ReconRunEntity run = require(runId);
        run.markRunning(jobExecutionId, Instant.now(clock));
    }

    /**
     * Persists breaks for a run, skipping any that are already present. Called once per partition,
     * so it must tolerate being invoked concurrently for disjoint merchant sets.
     */
    @Transactional
    public int recordOutcomes(Long runId, List<MatchOutcome> outcomes) {
        ReconRunEntity run = require(runId);
        List<ReconExceptionEntity> toInsert = new ArrayList<>();
        for (MatchOutcome outcome : outcomes) {
            if (!outcome.isException()) {
                continue;
            }
            boolean alreadyRecorded = exceptions.existsByRunIdAndMerchantIdAndExternalRefAndStatus(
                    runId, outcome.key().merchantId(), outcome.key().externalRef(), outcome.status());
            if (alreadyRecorded) {
                continue;
            }
            toInsert.add(ReconExceptionEntity.from(outcome, run));
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        exceptions.saveAll(toInsert);
        return toInsert.size();
    }

    @Transactional
    public ReconRunEntity complete(Long runId, ReconciliationSummary summary) {
        ReconRunEntity run = require(runId);
        Instant now = Instant.now(clock);
        run.complete(summary, now);
        outbox.record(
                "ReconRun",
                new ReconEvents.RunCompleted(
                        run.getId(),
                        run.getBusinessDate(),
                        summary.matchedKeys(),
                        summary.exceptionKeys(),
                        summary.matchRatePercent(),
                        summary.exposure().amount(),
                        summary.exposure().currencyCode(),
                        now));
        log.info(
                "Run {} finished: {} matched, {} exceptions, {}% match rate, exposure {}",
                run.getRunKey(),
                summary.matchedKeys(),
                summary.exceptionKeys(),
                summary.matchRatePercent(),
                summary.exposure());
        return run;
    }

    @Transactional
    public void fail(Long runId, String reason) {
        ReconRunEntity run = require(runId);
        Instant now = Instant.now(clock);
        run.fail(reason, now);
        outbox.record("ReconRun", new ReconEvents.RunFailed(run.getId(), run.getBusinessDate(), reason, now));
    }

    @Transactional
    public void markStopped(Long runId) {
        require(runId).markStopped(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<ReconRunEntity> findById(Long runId) {
        return runs.findById(runId);
    }

    @Transactional(readOnly = true)
    public Optional<ReconRunEntity> findByKey(LocalDate businessDate, String profile) {
        return runs.findByRunKey(runKey(businessDate, profile));
    }

    @Transactional(readOnly = true)
    public List<ReconRunEntity> recentRuns(int limit) {
        return runs.findAllByOrderByBusinessDateDescIdDesc(Limit.of(Math.clamp(limit, 1, 200)));
    }

    @Transactional(readOnly = true)
    public List<ReconRunRepository.RunTrendRow> trend(LocalDate from) {
        return runs.findTrend(from, List.of(RunStatus.COMPLETED, RunStatus.COMPLETED_WITH_BREAKS)).stream()
                .sorted(Comparator.comparing(ReconRunRepository.RunTrendRow::getBusinessDate))
                .toList();
    }

    private ReconRunEntity require(Long runId) {
        return runs.findById(runId).orElseThrow(() -> new ReconRunNotFoundException(runId));
    }

    public static final class ReconRunNotFoundException extends RuntimeException {
        private final Long runId;

        public ReconRunNotFoundException(Long runId) {
            super("Reconciliation run " + runId + " does not exist");
            this.runId = runId;
        }

        public Long runId() {
            return runId;
        }
    }
}
