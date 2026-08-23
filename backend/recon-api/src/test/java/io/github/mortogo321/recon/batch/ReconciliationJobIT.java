package io.github.mortogo321.recon.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.mortogo321.recon.batch.service.ReconJobOperations;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.entity.RunStatus;
import io.github.mortogo321.recon.core.repository.ReconExceptionRepository;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.domain.match.MatchStatus;

/**
 * The test that matters most: the whole job, against both databases, asserting the numbers.
 *
 * <p>The seeded demo day is built so that every classification occurs at least once and every
 * amount is derivable by hand — 100 settlement rows against 94 ledger postings, four of them
 * reversals or chargebacks that are out of scope, one transaction delivered twice, one settled in
 * the wrong currency, and two whose fee rounding differs by 0.40. Asserting the resulting totals
 * pins the business meaning of the pipeline, not just that it ran: if a future change silently
 * starts counting excluded rows as breaks, or absorbs a 25.00 variance, this fails with the number.
 *
 * <p>It runs on the local profile, so it needs no Docker; the same assertions hold against MySQL and
 * Oracle, which is the point of keeping the mappers inside the dialect subset both understand.
 */
@SpringBootTest(
        properties = {
            // Own databases: several @SpringBootTest contexts in one JVM would otherwise share the
            // named in-memory instance and drop each other's tables.
            "spring.datasource.url=jdbc:h2:mem:recon-job-it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "recon.legacy.datasource.url=jdbc:h2:mem:legacy-job-it;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            // The dispatcher would otherwise fire mid-assertion and log noise into the run.
            "recon.outbox.dispatch-interval=PT1H"
        })
@ActiveProfiles("local")
class ReconciliationJobIT {

    private static final LocalDate DEMO_DAY = LocalDate.of(2026, 8, 20);

    private static final Set<String> TERMINAL_BATCH_STATUSES =
            Set.of("COMPLETED", "FAILED", "STOPPED", "ABANDONED");

    @Autowired
    private ReconJobOperations jobs;

    @Autowired
    private ReconRunService runs;

    @Autowired
    private ReconExceptionRepository exceptions;

    @Test
    @DisplayName("reconciles the demo day to the exact figures the seed data implies")
    void reconcilesTheDemoDay() {
        ReconRunEntity run = runToCompletion(DEMO_DAY, "default").run();

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED_WITH_BREAKS);
        assertThat(run.getSettlementRows()).isEqualTo(100);
        assertThat(run.getLedgerRows()).isEqualTo(94);
        // Pending, reversed, charged-back and rejected rows are in the feed but not reconcilable.
        assertThat(run.getExcludedRows()).isEqualTo(4);
        assertThat(run.getExceptionKeys()).isEqualTo(13);
        assertThat(run.getMatchRate()).isEqualByComparingTo(new BigDecimal("86.73"));
        assertThat(run.getExposure().amount()).isEqualByComparingTo(new BigDecimal("29115.80"));

        assertThat(breakdownOf(run))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        MatchStatus.AMOUNT_MISMATCH, 3L,
                        MatchStatus.MISSING_IN_LEDGER, 4L,
                        MatchStatus.MISSING_IN_SETTLEMENT, 3L,
                        MatchStatus.DUPLICATE_SETTLEMENT, 1L,
                        MatchStatus.CURRENCY_MISMATCH, 2L));
    }

    @Test
    @DisplayName("a wider tolerance profile absorbs the fee variances instead of raising them")
    void relaxedProfileAbsorbsFeeVariances() {
        ReconRunEntity run = runToCompletion(DEMO_DAY, "relaxed").run();

        // Same data, same day: only the configured allowance changed, and the three 25.00 fee
        // variances stop being breaks. This is the case for tolerances being configuration.
        assertThat(breakdownOf(run)).doesNotContainKey(MatchStatus.AMOUNT_MISMATCH);
        assertThat(run.getExceptionKeys()).isEqualTo(10);
    }

    @Test
    @DisplayName("an exact-match profile raises the roundings the default profile absorbs")
    void strictProfileRaisesRoundingDifferences() {
        ReconRunEntity run = runToCompletion(DEMO_DAY, "strict").run();

        // The two 0.40 acquirer roundings the default profile lets through become breaks here.
        assertThat(breakdownOf(run)).containsEntry(MatchStatus.AMOUNT_MISMATCH, 5L);
        assertThat(run.getExceptionKeys()).isEqualTo(15);
    }

    @Test
    @DisplayName("re-running a completed day is a fresh attempt, not a duplicate break")
    void reRunningADayDoesNotDuplicateBreaks() {
        Completed first = runToCompletion(DEMO_DAY, "default");
        long breaksAfterFirst = totalBreaks(first.run());

        Completed second = runToCompletion(DEMO_DAY, "default");

        // A second launch is a new job execution: identifying job parameters carry an attempt
        // number, without which Spring Batch would refuse a completed instance outright and
        // re-running a corrected day would be impossible.
        assertThat(second.executionId()).isNotEqualTo(first.executionId());
        // Same run row though - it is keyed on business date and profile, not on the attempt - and
        // the writer skips breaks already recorded, so a re-run tops up rather than doubling.
        assertThat(second.run().getId()).isEqualTo(first.run().getId());
        assertThat(totalBreaks(second.run())).isEqualTo(breaksAfterFirst);
    }

    @Test
    @DisplayName("an unknown tolerance profile is refused before any job is started")
    void unknownProfileIsRefusedUpFront() {
        assertThatThrownBy(() -> jobs.launch(DEMO_DAY, "no-such-profile"))
                .hasMessageContaining("no-such-profile");
        assertThat(runs.findByKey(DEMO_DAY, "no-such-profile")).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private record Completed(ReconRunEntity run, long executionId) {}

    /**
     * Launches through the same path the API uses. The job runs on a virtual thread, so the wait is
     * on <em>this</em> execution reaching a terminal batch status — not on the run row looking
     * finished, which it already does when the same day was reconciled by an earlier test.
     */
    private Completed runToCompletion(LocalDate businessDate, String profile) {
        runs.openRun(businessDate, profile);
        long executionId = jobs.launch(businessDate, profile).executionId();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            String status = jobs.findExecution(executionId)
                    .map(ReconJobOperations.JobHandle::status)
                    .orElse("UNKNOWN");
            if (TERMINAL_BATCH_STATUSES.contains(status)) {
                return new Completed(runs.findByKey(businessDate, profile).orElseThrow(), executionId);
            }
            sleep();
        }
        throw new AssertionError("Execution " + executionId + " for " + businessDate + " did not finish in 60s");
    }

    /** Uses the same projection query the dashboard does, so the read path is covered too. */
    private Map<MatchStatus, Long> breakdownOf(ReconRunEntity run) {
        Map<MatchStatus, Long> counts = new EnumMap<>(MatchStatus.class);
        exceptions.breakdownByRun(run.getId()).forEach(row -> counts.merge(row.getStatus(), row.getTotal(), Long::sum));
        return counts;
    }

    private long totalBreaks(ReconRunEntity run) {
        return breakdownOf(run).values().stream().mapToLong(Long::longValue).sum();
    }

    private static void sleep() {
        try {
            Thread.sleep(Duration.ofMillis(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the job", e);
        }
    }
}
