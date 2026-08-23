package io.github.mortogo321.recon.domain.match;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import io.github.mortogo321.recon.domain.money.Money;

/**
 * How much drift is acceptable before a difference becomes an exception.
 *
 * <p>Acquirers routinely round fees differently to us, so a flat absolute tolerance is not enough
 * for large tickets and a pure percentage tolerance is not enough for small ones. The allowance is
 * therefore {@code max(absolute, expected * bps / 10_000)}.
 */
public record ToleranceRule(Money absolute, int bps) {

    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    public ToleranceRule {
        Objects.requireNonNull(absolute, "absolute");
        if (absolute.isNegative()) {
            throw new IllegalArgumentException("absolute tolerance must not be negative");
        }
        if (bps < 0) {
            throw new IllegalArgumentException("bps must not be negative");
        }
    }

    public static ToleranceRule exact(String currencyCode) {
        return new ToleranceRule(Money.of("0.00", currencyCode), 0);
    }

    /** Allowance for a given expected amount, in the expected amount's currency. */
    public Money allowanceFor(Money expected) {
        Objects.requireNonNull(expected, "expected");
        BigDecimal relative = expected.amount().abs()
                .multiply(BigDecimal.valueOf(bps))
                .divide(BPS_DIVISOR, expected.amount().scale(), RoundingMode.HALF_UP);
        Money relativeMoney = new Money(relative, expected.currency());
        if (!absolute.sameCurrencyAs(expected)) {
            // A tolerance configured in another currency cannot be applied; fall back to relative only.
            return relativeMoney;
        }
        return relativeMoney.compareTo(absolute) >= 0 ? relativeMoney : absolute;
    }

    public boolean accepts(Money expected, Money actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        if (!expected.sameCurrencyAs(actual)) {
            return false;
        }
        Money delta = expected.subtract(actual).abs();
        return delta.compareTo(allowanceFor(expected)) <= 0;
    }
}
