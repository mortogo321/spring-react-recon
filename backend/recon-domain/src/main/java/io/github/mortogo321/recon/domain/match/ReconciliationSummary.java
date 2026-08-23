package io.github.mortogo321.recon.domain.match;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import io.github.mortogo321.recon.domain.money.Money;

/** Roll-up persisted on the run row and rendered as the dashboard KPI strip. */
public record ReconciliationSummary(
        int settlementRows,
        int ledgerRows,
        int excludedRows,
        Map<MatchStatus, Integer> countByStatus,
        Money matchedAmount,
        Money exposure) {

    public ReconciliationSummary {
        Objects.requireNonNull(countByStatus, "countByStatus");
        Objects.requireNonNull(matchedAmount, "matchedAmount");
        Objects.requireNonNull(exposure, "exposure");
        countByStatus = Collections.unmodifiableMap(new EnumMap<>(countByStatus));
    }

    public int count(MatchStatus status) {
        return countByStatus.getOrDefault(status, 0);
    }

    public int matchedKeys() {
        return count(MatchStatus.MATCHED) + count(MatchStatus.MATCHED_WITHIN_TOLERANCE);
    }

    public int exceptionKeys() {
        return countByStatus.entrySet().stream()
                .filter(e -> e.getKey().isException())
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public int totalKeys() {
        return matchedKeys() + exceptionKeys();
    }

    /** Percentage of keys that reconciled cleanly, to two decimal places. */
    public BigDecimal matchRatePercent() {
        int total = totalKeys();
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(matchedKeys())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    public boolean hasCriticalBreaks() {
        return countByStatus.entrySet().stream()
                .anyMatch(e -> e.getKey().severity() == MatchSeverity.CRITICAL && e.getValue() > 0);
    }
}
