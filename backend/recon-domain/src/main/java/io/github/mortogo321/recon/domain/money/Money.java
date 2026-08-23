package io.github.mortogo321.recon.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount. Amounts are always normalised to the currency's default fraction
 * digits so that {@code equals} is value-based rather than scale-sensitive — the single most
 * common source of false reconciliation breaks when {@link BigDecimal} is passed around raw.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int scale = currency.getDefaultFractionDigits() < 0 ? 2 : currency.getDefaultFractionDigits();
        amount = amount.setScale(scale, RoundingMode.HALF_EVEN);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        return new Money(amount.add(sameCurrency(other).amount), currency);
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(sameCurrency(other).amount), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean sameCurrencyAs(Money other) {
        return currency.equals(Objects.requireNonNull(other, "other").currency);
    }

    /** Amount expressed in minor units (satang, cents) — the form legacy ledgers usually store. */
    public long toMinorUnits() {
        return amount.movePointRight(amount.scale()).longValueExact();
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(sameCurrency(other).amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency.getCurrencyCode(), other.currency.getCurrencyCode());
        }
        return other;
    }

    /** Thrown rather than silently converting: cross-currency arithmetic is always a data bug here. */
    public static final class CurrencyMismatchException extends IllegalArgumentException {
        private final String left;
        private final String right;

        public CurrencyMismatchException(String left, String right) {
            super("Cannot combine " + left + " with " + right);
            this.left = left;
            this.right = right;
        }

        public String left() {
            return left;
        }

        public String right() {
            return right;
        }
    }
}
