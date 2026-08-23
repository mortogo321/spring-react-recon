package io.github.mortogo321.recon.domain.match;

import java.util.List;
import java.util.Objects;

import io.github.mortogo321.recon.domain.model.MatchKey;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * Result of reconciling one {@link MatchKey}. Modelled as a sealed hierarchy so that every
 * consumer — the batch writer, the REST mapper, the alert evaluator — is forced by the compiler
 * to handle new classifications when they are added.
 */
public sealed interface MatchOutcome {

    MatchKey key();

    MatchStatus status();

    /** Monetary exposure this outcome represents; zero for a clean match. */
    Money exposure();

    /** Human-readable explanation persisted alongside the exception for the operator. */
    String detail();

    default MatchSeverity severity() {
        return status().severity();
    }

    default boolean isException() {
        return status().isException();
    }

    record Matched(MatchKey key, Money amount) implements MatchOutcome {
        public Matched {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(amount, "amount");
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.MATCHED;
        }

        @Override
        public Money exposure() {
            return Money.zero(amount.currency());
        }

        @Override
        public String detail() {
            return "Settlement and ledger agree at " + amount;
        }
    }

    record ToleranceMatched(MatchKey key, Money settlement, Money ledger, Money allowance)
            implements MatchOutcome {
        public ToleranceMatched {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(allowance, "allowance");
        }

        public Money delta() {
            return settlement.subtract(ledger);
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.MATCHED_WITHIN_TOLERANCE;
        }

        @Override
        public Money exposure() {
            return Money.zero(settlement.currency());
        }

        @Override
        public String detail() {
            return "Absorbed delta " + delta() + " within allowance " + allowance;
        }
    }

    record AmountMismatch(MatchKey key, Money settlement, Money ledger, Money allowance)
            implements MatchOutcome {
        public AmountMismatch {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(allowance, "allowance");
        }

        public Money delta() {
            return settlement.subtract(ledger);
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.AMOUNT_MISMATCH;
        }

        @Override
        public Money exposure() {
            return delta().abs();
        }

        @Override
        public String detail() {
            return "Settlement " + settlement + " vs ledger " + ledger + " (delta " + delta()
                    + ", allowance " + allowance + ")";
        }
    }

    record MissingInLedger(MatchKey key, Money settlement, String txnId) implements MatchOutcome {
        public MissingInLedger {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(settlement, "settlement");
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.MISSING_IN_LEDGER;
        }

        @Override
        public Money exposure() {
            return settlement.abs();
        }

        @Override
        public String detail() {
            return "Acquirer settled " + settlement + " (txn " + txnId + ") with no ledger posting";
        }
    }

    record MissingInSettlement(MatchKey key, Money ledger, String entryId) implements MatchOutcome {
        public MissingInSettlement {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(ledger, "ledger");
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.MISSING_IN_SETTLEMENT;
        }

        @Override
        public Money exposure() {
            return ledger.abs();
        }

        @Override
        public String detail() {
            return "Ledger posted " + ledger + " (entry " + entryId + ") not present in settlement feed";
        }
    }

    record DuplicateSettlement(MatchKey key, String txnId, int occurrences, Money each)
            implements MatchOutcome {
        public DuplicateSettlement {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(each, "each");
            if (occurrences < 2) {
                throw new IllegalArgumentException("a duplicate implies at least two occurrences");
            }
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.DUPLICATE_SETTLEMENT;
        }

        @Override
        public Money exposure() {
            Money total = each.abs();
            for (int i = 2; i <= occurrences; i++) {
                total = total.add(each.abs());
            }
            return total.subtract(each.abs());
        }

        @Override
        public String detail() {
            return "Txn " + txnId + " appears " + occurrences + " times at " + each;
        }
    }

    record CurrencyMismatch(MatchKey key, String settlementCurrency, String ledgerCurrency, Money settlement)
            implements MatchOutcome {
        public CurrencyMismatch {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(settlementCurrency, "settlementCurrency");
            Objects.requireNonNull(ledgerCurrency, "ledgerCurrency");
            Objects.requireNonNull(settlement, "settlement");
        }

        @Override
        public MatchStatus status() {
            return MatchStatus.CURRENCY_MISMATCH;
        }

        @Override
        public Money exposure() {
            return settlement.abs();
        }

        @Override
        public String detail() {
            return "Settlement in " + settlementCurrency + " but ledger in " + ledgerCurrency;
        }
    }

    /** Triage order used by the console's default sort: worst money first. */
    static List<MatchOutcome> triageOrder(List<MatchOutcome> outcomes) {
        return outcomes.stream()
                .sorted(java.util.Comparator
                        .comparing((MatchOutcome o) -> o.severity().ordinal())
                        .reversed()
                        .thenComparing((MatchOutcome o) -> o.exposure().amount().negate())
                        .thenComparing(MatchOutcome::key))
                .toList();
    }
}
