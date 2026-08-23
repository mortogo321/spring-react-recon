package io.github.mortogo321.recon.domain.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.mortogo321.recon.domain.model.LedgerEntry;
import io.github.mortogo321.recon.domain.model.MatchKey;
import io.github.mortogo321.recon.domain.model.SettlementRecord;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * The reconciliation algorithm. Deliberately stateless, side-effect free and framework free:
 * every interesting rule in this system is expressible as a pure function of two row sets plus a
 * tolerance, which is what makes it cheap to test exhaustively.
 *
 * <p>Matching is many-to-many aware: an acquirer may split one ledger posting across several
 * settlement rows (partial captures), so amounts are aggregated per {@link MatchKey} before
 * comparison. True duplicates — the same {@code txnId} delivered twice — are reported separately
 * and excluded from the aggregate so that one ingestion fault does not cascade into a fake
 * amount break on every key in the file.
 */
public final class ReconciliationEngine {

    private final Currency reportingCurrency;

    public ReconciliationEngine(Currency reportingCurrency) {
        this.reportingCurrency = Objects.requireNonNull(reportingCurrency, "reportingCurrency");
    }

    public static ReconciliationEngine reportingIn(String currencyCode) {
        return new ReconciliationEngine(Currency.getInstance(currencyCode));
    }

    public ReconciliationResult reconcile(
            List<SettlementRecord> settlements, List<LedgerEntry> ledgerEntries, ToleranceRule tolerance) {
        Objects.requireNonNull(settlements, "settlements");
        Objects.requireNonNull(ledgerEntries, "ledgerEntries");
        Objects.requireNonNull(tolerance, "tolerance");

        List<SettlementRecord> inScope = settlements.stream().filter(SettlementRecord::isReconcilable).toList();
        int excluded = settlements.size() - inScope.size();

        Map<MatchKey, List<SettlementRecord>> settlementsByKey = inScope.stream()
                .collect(Collectors.groupingBy(SettlementRecord::matchKey, LinkedHashMap::new, Collectors.toList()));
        Map<MatchKey, List<LedgerEntry>> ledgerByKey = ledgerEntries.stream()
                .filter(LedgerEntry::isReconcilable)
                .collect(Collectors.groupingBy(LedgerEntry::matchKey, LinkedHashMap::new, Collectors.toList()));

        List<MatchOutcome> outcomes = new ArrayList<>();
        Set<MatchKey> allKeys = new TreeSet<>(settlementsByKey.keySet());
        allKeys.addAll(ledgerByKey.keySet());

        for (MatchKey key : allKeys) {
            List<SettlementRecord> rows = settlementsByKey.getOrDefault(key, List.of());
            List<LedgerEntry> entries = ledgerByKey.getOrDefault(key, List.of());

            List<SettlementRecord> deduped = extractDuplicates(key, rows, outcomes);
            outcomes.addAll(classify(key, deduped, entries, tolerance));
        }

        return new ReconciliationResult(outcomes, summarise(settlements.size(), ledgerEntries.size(), excluded, outcomes));
    }

    /**
     * Emits a {@link MatchOutcome.DuplicateSettlement} for every repeated {@code txnId} and returns
     * the de-duplicated rows (first occurrence wins) so the amount comparison stays meaningful.
     */
    private List<SettlementRecord> extractDuplicates(
            MatchKey key, List<SettlementRecord> rows, List<MatchOutcome> sink) {
        if (rows.size() < 2) {
            return rows;
        }
        Map<String, List<SettlementRecord>> byTxn = rows.stream()
                .collect(Collectors.groupingBy(SettlementRecord::txnId, LinkedHashMap::new, Collectors.toList()));
        List<SettlementRecord> deduped = new ArrayList<>(byTxn.size());
        byTxn.forEach((txnId, group) -> {
            deduped.add(group.getFirst());
            if (group.size() > 1) {
                sink.add(new MatchOutcome.DuplicateSettlement(key, txnId, group.size(), group.getFirst().net()));
            }
        });
        return deduped;
    }

    private List<MatchOutcome> classify(
            MatchKey key, List<SettlementRecord> rows, List<LedgerEntry> entries, ToleranceRule tolerance) {
        boolean hasSettlement = !rows.isEmpty();
        boolean hasLedger = !entries.isEmpty();

        if (hasSettlement && mixedCurrency(rows, SettlementRecord::net)) {
            return List.of(currencyMismatch(key, rows, rows));
        }
        if (hasLedger && mixedCurrency(entries, LedgerEntry::amount)) {
            return List.of(currencyMismatch(key, rows, entries));
        }

        if (hasSettlement && !hasLedger) {
            return List.of(new MatchOutcome.MissingInLedger(key, sum(rows, SettlementRecord::net), rows.getFirst().txnId()));
        }
        if (!hasSettlement && hasLedger) {
            return List.of(
                    new MatchOutcome.MissingInSettlement(key, sum(entries, LedgerEntry::amount), entries.getFirst().entryId()));
        }
        if (!hasSettlement) {
            // Only reachable when every settlement row for the key was a duplicate of a dropped row.
            return List.of();
        }

        Money settlementTotal = sum(rows, SettlementRecord::net);
        Money ledgerTotal = sum(entries, LedgerEntry::amount);

        if (!settlementTotal.sameCurrencyAs(ledgerTotal)) {
            return List.of(new MatchOutcome.CurrencyMismatch(
                    key, settlementTotal.currencyCode(), ledgerTotal.currencyCode(), settlementTotal));
        }

        Money allowance = tolerance.allowanceFor(settlementTotal);
        if (settlementTotal.equals(ledgerTotal)) {
            return List.of(new MatchOutcome.Matched(key, settlementTotal));
        }
        if (tolerance.accepts(settlementTotal, ledgerTotal)) {
            return List.of(new MatchOutcome.ToleranceMatched(key, settlementTotal, ledgerTotal, allowance));
        }
        return List.of(new MatchOutcome.AmountMismatch(key, settlementTotal, ledgerTotal, allowance));
    }

    private <T> boolean mixedCurrency(List<T> rows, Function<T, Money> amount) {
        return rows.stream().map(amount).map(Money::currencyCode).distinct().count() > 1;
    }

    private MatchOutcome currencyMismatch(MatchKey key, List<SettlementRecord> rows, List<?> offending) {
        List<String> currencies = offending.stream()
                .map(row -> row instanceof SettlementRecord s ? s.net() : ((LedgerEntry) row).amount())
                .map(Money::currencyCode)
                .distinct()
                .sorted()
                .toList();
        Money exposure = rows.isEmpty() ? Money.zero(reportingCurrency) : rows.getFirst().net();
        return new MatchOutcome.CurrencyMismatch(key, currencies.getFirst(), currencies.getLast(), exposure);
    }

    private <T> Money sum(List<T> rows, Function<T, Money> amount) {
        return rows.stream()
                .map(amount)
                .reduce(Money::add)
                .orElseGet(() -> Money.zero(reportingCurrency));
    }

    private ReconciliationSummary summarise(
            int settlementRows, int ledgerRows, int excluded, List<MatchOutcome> outcomes) {
        Map<MatchStatus, Integer> counts = new EnumMap<>(MatchStatus.class);
        Money matched = Money.zero(reportingCurrency);
        Money exposure = Money.zero(reportingCurrency);

        for (MatchOutcome outcome : outcomes) {
            counts.merge(outcome.status(), 1, Integer::sum);
            // Exhaustive over the sealed hierarchy: adding a classification breaks the build here first.
            Money contribution = switch (outcome) {
                case MatchOutcome.Matched m -> m.amount();
                case MatchOutcome.ToleranceMatched t -> t.settlement();
                case MatchOutcome.AmountMismatch ignored -> null;
                case MatchOutcome.MissingInLedger ignored -> null;
                case MatchOutcome.MissingInSettlement ignored -> null;
                case MatchOutcome.DuplicateSettlement ignored -> null;
                case MatchOutcome.CurrencyMismatch ignored -> null;
            };
            if (contribution != null && contribution.currency().equals(reportingCurrency)) {
                matched = matched.add(contribution);
            }
            Money risk = outcome.exposure();
            if (risk.currency().equals(reportingCurrency)) {
                exposure = exposure.add(risk);
            }
        }
        return new ReconciliationSummary(settlementRows, ledgerRows, excluded, counts, matched, exposure);
    }

    /** Stable ordering helper used by tests and by the CSV export. */
    public static Comparator<MatchOutcome> byKey() {
        return Comparator.comparing(MatchOutcome::key);
    }
}
