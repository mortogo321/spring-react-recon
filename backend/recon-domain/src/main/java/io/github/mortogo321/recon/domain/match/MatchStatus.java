package io.github.mortogo321.recon.domain.match;

/**
 * The classification assigned to a single match key. Only {@link #MATCHED} and
 * {@link #MATCHED_WITHIN_TOLERANCE} are clean; everything else becomes an exception
 * that an operator has to work.
 */
public enum MatchStatus {
    MATCHED(MatchSeverity.INFO),
    MATCHED_WITHIN_TOLERANCE(MatchSeverity.INFO),
    AMOUNT_MISMATCH(MatchSeverity.CRITICAL),
    MISSING_IN_LEDGER(MatchSeverity.CRITICAL),
    MISSING_IN_SETTLEMENT(MatchSeverity.WARNING),
    DUPLICATE_SETTLEMENT(MatchSeverity.CRITICAL),
    CURRENCY_MISMATCH(MatchSeverity.CRITICAL);

    private final MatchSeverity severity;

    MatchStatus(MatchSeverity severity) {
        this.severity = severity;
    }

    public MatchSeverity severity() {
        return severity;
    }

    public boolean isException() {
        return this != MATCHED && this != MATCHED_WITHIN_TOLERANCE;
    }
}
