package io.github.mortogo321.recon.domain;

import static io.github.mortogo321.recon.domain.Fixtures.ledger;
import static io.github.mortogo321.recon.domain.Fixtures.ledgerIn;
import static io.github.mortogo321.recon.domain.Fixtures.settlement;
import static io.github.mortogo321.recon.domain.Fixtures.settlementIn;
import static io.github.mortogo321.recon.domain.Fixtures.thb;
import static io.github.mortogo321.recon.domain.Fixtures.voidedLedger;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;
import io.github.mortogo321.recon.domain.match.ReconciliationEngine;
import io.github.mortogo321.recon.domain.match.ReconciliationResult;
import io.github.mortogo321.recon.domain.match.ToleranceRule;
import io.github.mortogo321.recon.domain.model.SettlementStatus;

class ReconciliationEngineTest {

    private final ReconciliationEngine engine = ReconciliationEngine.reportingIn("THB");
    private final ToleranceRule exact = ToleranceRule.exact("THB");
    private final ToleranceRule lenient = new ToleranceRule(thb("1.00"), 10);

    @Nested
    @DisplayName("clean matches")
    class CleanMatches {

        @Test
        void netAmountIsComparedNotGross() {
            // Acquirer settles 100.00 gross and keeps 2.50 in fees; the ledger only ever sees the net.
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00", "2.50")), List.of(ledger("L1", "REF-1", "97.50")), exact);

            assertThat(result.outcomes()).singleElement().isInstanceOf(MatchOutcome.Matched.class);
            assertThat(result.exceptions()).isEmpty();
            assertThat(result.summary().matchedAmount()).isEqualTo(thb("97.50"));
            assertThat(result.summary().exposure()).isEqualTo(thb("0.00"));
        }

        @Test
        void splitSettlementRowsAggregateAgainstOneLedgerPosting() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "60.00"), settlement("T2", "REF-1", "40.00")),
                    List.of(ledger("L1", "REF-1", "100.00")),
                    exact);

            assertThat(result.exceptions()).isEmpty();
            assertThat(result.outcomes())
                    .singleElement()
                    .isInstanceOf(MatchOutcome.Matched.class)
                    .extracting(o -> ((MatchOutcome.Matched) o).amount())
                    .isEqualTo(thb("100.00"));
        }

        @Test
        void differenceInsideToleranceIsAbsorbedButStillReported() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00")), List.of(ledger("L1", "REF-1", "99.50")), lenient);

            assertThat(result.exceptions()).isEmpty();
            MatchOutcome.ToleranceMatched matched =
                    (MatchOutcome.ToleranceMatched) result.outcomes().getFirst();
            assertThat(matched.delta()).isEqualTo(thb("0.50"));
            assertThat(matched.allowance()).isEqualTo(thb("1.00"));
            assertThat(matched.exposure()).isEqualTo(thb("0.00"));
        }
    }

    @Nested
    @DisplayName("breaks")
    class Breaks {

        @Test
        void amountMismatchCarriesTheDeltaAndTheAllowanceItBusted() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00")), List.of(ledger("L1", "REF-1", "90.00")), lenient);

            MatchOutcome.AmountMismatch break_ = (MatchOutcome.AmountMismatch) result.outcomes().getFirst();
            assertThat(break_.status()).isEqualTo(MatchStatus.AMOUNT_MISMATCH);
            assertThat(break_.delta()).isEqualTo(thb("10.00"));
            assertThat(break_.allowance()).isEqualTo(thb("1.00"));
            assertThat(break_.exposure()).isEqualTo(thb("10.00"));
            assertThat(break_.severity()).isEqualTo(MatchSeverity.CRITICAL);
        }

        @Test
        void settlementWithNoLedgerPostingIsMoneyWeOwe() {
            ReconciliationResult result =
                    engine.reconcile(List.of(settlement("T1", "REF-1", "100.00")), List.of(), exact);

            assertThat(result.outcomes()).singleElement().isInstanceOf(MatchOutcome.MissingInLedger.class);
            assertThat(result.summary().exposure()).isEqualTo(thb("100.00"));
            assertThat(result.summary().hasCriticalBreaks()).isTrue();
        }

        @Test
        void ledgerPostingWithNoSettlementIsAWarningNotACriticalBreak() {
            ReconciliationResult result = engine.reconcile(List.of(), List.of(ledger("L1", "REF-9", "42.00")), exact);

            MatchOutcome outcome = result.outcomes().getFirst();
            assertThat(outcome).isInstanceOf(MatchOutcome.MissingInSettlement.class);
            assertThat(outcome.severity()).isEqualTo(MatchSeverity.WARNING);
            assertThat(result.summary().hasCriticalBreaks()).isFalse();
        }

        @Test
        void repeatedTxnIdIsReportedAsDuplicateAndExcludedFromTheAggregate() {
            // The same txn delivered twice must not manufacture an amount break on the key.
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00"), settlement("T1", "REF-1", "100.00")),
                    List.of(ledger("L1", "REF-1", "100.00")),
                    exact);

            assertThat(result.outcomes())
                    .extracting(MatchOutcome::status)
                    .containsExactlyInAnyOrder(MatchStatus.DUPLICATE_SETTLEMENT, MatchStatus.MATCHED);

            MatchOutcome.DuplicateSettlement dup = result.outcomes().stream()
                    .filter(MatchOutcome.DuplicateSettlement.class::isInstance)
                    .map(MatchOutcome.DuplicateSettlement.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(dup.occurrences()).isEqualTo(2);
            assertThat(dup.exposure()).isEqualTo(thb("100.00"));
        }

        @Test
        void currencyMismatchIsFlaggedRatherThanThrowing() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlementIn("T1", "REF-1", "100.00", "USD")),
                    List.of(ledgerIn("L1", "REF-1", "100.00", "THB")),
                    exact);

            MatchOutcome.CurrencyMismatch mismatch =
                    (MatchOutcome.CurrencyMismatch) result.outcomes().getFirst();
            assertThat(mismatch.settlementCurrency()).isEqualTo("USD");
            assertThat(mismatch.ledgerCurrency()).isEqualTo("THB");
            // USD exposure must not leak into a THB reporting total.
            assertThat(result.summary().exposure()).isEqualTo(thb("0.00"));
        }

        @Test
        void mixedCurrenciesWithinOneSettlementKeyAreFlaggedOnce() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlementIn("T1", "REF-1", "10.00", "THB"), settlementIn("T2", "REF-1", "10.00", "USD")),
                    List.of(ledger("L1", "REF-1", "20.00")),
                    exact);

            assertThat(result.outcomes()).singleElement().isInstanceOf(MatchOutcome.CurrencyMismatch.class);
        }
    }

    @Nested
    @DisplayName("scoping")
    class Scoping {

        @Test
        void nonSettledStatusesAreExcludedAndCounted() {
            ReconciliationResult result = engine.reconcile(
                    List.of(
                            settlement("T1", "REF-1", "100.00"),
                            settlement("T2", "REF-2", "50.00", SettlementStatus.PENDING),
                            settlement("T3", "REF-3", "25.00", SettlementStatus.REVERSED)),
                    List.of(ledger("L1", "REF-1", "100.00")),
                    exact);

            assertThat(result.summary().excludedRows()).isEqualTo(2);
            assertThat(result.summary().settlementRows()).isEqualTo(3);
            assertThat(result.exceptions()).isEmpty();
        }

        @Test
        void voidedLedgerEntriesAreIgnored() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00")),
                    List.of(ledger("L1", "REF-1", "100.00"), voidedLedger("L2", "REF-1", "999.00")),
                    exact);

            assertThat(result.exceptions()).isEmpty();
        }

        @Test
        void emptyInputProducesAnEmptyButValidSummary() {
            ReconciliationResult result = engine.reconcile(List.of(), List.of(), exact);

            assertThat(result.outcomes()).isEmpty();
            assertThat(result.summary().totalKeys()).isZero();
            assertThat(result.summary().matchRatePercent()).isEqualTo(new java.math.BigDecimal("0.00"));
        }
    }

    @Nested
    @DisplayName("reporting")
    class Reporting {

        @Test
        void matchRateAndTriageOrderDriveTheConsole() {
            ReconciliationResult result = engine.reconcile(
                    List.of(
                            settlement("T1", "REF-1", "100.00"),
                            settlement("T2", "REF-2", "200.00"),
                            settlement("T3", "REF-3", "300.00"),
                            settlement("T4", "REF-4", "400.00")),
                    List.of(ledger("L1", "REF-1", "100.00"), ledger("L2", "REF-2", "150.00")),
                    exact);

            assertThat(result.summary().matchedKeys()).isEqualTo(1);
            assertThat(result.summary().exceptionKeys()).isEqualTo(3);
            assertThat(result.summary().matchRatePercent()).isEqualTo(new java.math.BigDecimal("25.00"));

            // Critical first, then biggest exposure first: 400 and 300 missing, then the 50 mismatch.
            assertThat(result.triaged())
                    .extracting(o -> o.key().externalRef())
                    .containsExactly("REF-4", "REF-3", "REF-2");
        }

        @Test
        void everyOutcomeExplainsItselfForTheOperator() {
            ReconciliationResult result = engine.reconcile(
                    List.of(settlement("T1", "REF-1", "100.00")), List.of(ledger("L1", "REF-1", "90.00")), exact);

            assertThat(result.outcomes()).allSatisfy(o -> assertThat(o.detail()).isNotBlank());
        }
    }
}
