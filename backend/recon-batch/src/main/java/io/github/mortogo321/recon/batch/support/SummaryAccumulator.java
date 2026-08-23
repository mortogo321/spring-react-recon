package io.github.mortogo321.recon.batch.support;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.batch.infrastructure.item.ExecutionContext;

import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.MatchStatus;
import io.github.mortogo321.recon.domain.match.ReconciliationSummary;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * Accumulates a partition's totals into its {@link ExecutionContext} so they survive a restart and
 * can be summed across workers when the job finalises.
 *
 * <p>Only primitives and strings are written: the execution context is serialised into the batch
 * metadata tables, and putting a rich object graph in there turns every future refactor of that
 * object into a data-migration problem.
 */
public final class SummaryAccumulator {

    private final ExecutionContext context;
    private final Currency currency;

    public SummaryAccumulator(ExecutionContext context, Currency currency) {
        this.context = context;
        this.currency = currency;
        context.putString(BatchKeys.SUM_CURRENCY, currency.getCurrencyCode());
    }

    public void add(MatchOutcome outcome) {
        bump(BatchKeys.SUM_COUNT_PREFIX + outcome.status().name(), 1);
        if (!outcome.isException()) {
            Money amount = switch (outcome) {
                case MatchOutcome.Matched m -> m.amount();
                case MatchOutcome.ToleranceMatched t -> t.settlement();
                default -> null;
            };
            addAmount(BatchKeys.SUM_MATCHED_AMOUNT, amount);
        }
        addAmount(BatchKeys.SUM_EXPOSURE_AMOUNT, outcome.exposure());
    }

    public void addRows(long settlementRows, long ledgerRows, long excludedRows) {
        bump(BatchKeys.SUM_SETTLEMENT_ROWS, settlementRows);
        bump(BatchKeys.SUM_LEDGER_ROWS, ledgerRows);
        bump(BatchKeys.SUM_EXCLUDED_ROWS, excludedRows);
    }

    private void bump(String key, long delta) {
        context.putLong(key, context.containsKey(key) ? context.getLong(key) + delta : delta);
    }

    private void addAmount(String key, Money money) {
        if (money == null || !money.currency().equals(currency)) {
            return;
        }
        BigDecimal current = new BigDecimal(context.getString(key, "0"));
        context.putString(key, current.add(money.amount()).toPlainString());
    }

    /** Folds any number of partition contexts into the single summary persisted on the run. */
    public static ReconciliationSummary merge(Iterable<ExecutionContext> contexts, Currency fallbackCurrency) {
        long settlementRows = 0;
        long ledgerRows = 0;
        long excludedRows = 0;
        BigDecimal matched = BigDecimal.ZERO;
        BigDecimal exposure = BigDecimal.ZERO;
        Currency currency = fallbackCurrency;
        Map<MatchStatus, Integer> counts = new EnumMap<>(MatchStatus.class);

        for (ExecutionContext ctx : contexts) {
            settlementRows += ctx.getLong(BatchKeys.SUM_SETTLEMENT_ROWS, 0);
            ledgerRows += ctx.getLong(BatchKeys.SUM_LEDGER_ROWS, 0);
            excludedRows += ctx.getLong(BatchKeys.SUM_EXCLUDED_ROWS, 0);
            matched = matched.add(new BigDecimal(ctx.getString(BatchKeys.SUM_MATCHED_AMOUNT, "0")));
            exposure = exposure.add(new BigDecimal(ctx.getString(BatchKeys.SUM_EXPOSURE_AMOUNT, "0")));
            String code = ctx.getString(BatchKeys.SUM_CURRENCY, null);
            if (code != null) {
                currency = Currency.getInstance(code);
            }
            for (MatchStatus status : MatchStatus.values()) {
                long value = ctx.getLong(BatchKeys.SUM_COUNT_PREFIX + status.name(), 0);
                if (value > 0) {
                    counts.merge(status, (int) value, Integer::sum);
                }
            }
        }
        return new ReconciliationSummary(
                (int) settlementRows,
                (int) ledgerRows,
                (int) excludedRows,
                counts,
                new Money(matched, currency),
                new Money(exposure, currency));
    }
}
