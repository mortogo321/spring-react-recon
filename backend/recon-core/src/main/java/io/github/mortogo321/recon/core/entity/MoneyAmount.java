package io.github.mortogo321.recon.core.entity;

import java.math.BigDecimal;
import java.util.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.mortogo321.recon.domain.money.Money;

/**
 * Persistent form of {@link Money}: amount and currency always travel together as two columns,
 * which is the only way to keep a multi-currency ledger honest. Mutable by necessity (JPA needs a
 * no-arg constructor and field access) but never handed out — callers get a {@link Money} back.
 */
@Embeddable
public class MoneyAmount {

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(length = 3, nullable = false)
    private Currency currency;

    protected MoneyAmount() {
        // for JPA
    }

    private MoneyAmount(BigDecimal value, Currency currency) {
        this.value = value;
        this.currency = currency;
    }

    public static MoneyAmount from(Money money) {
        return money == null ? null : new MoneyAmount(money.amount(), money.currency());
    }

    public Money toMoney() {
        return new Money(value, currency);
    }

    public BigDecimal getValue() {
        return value;
    }

    public Currency getCurrency() {
        return currency;
    }
}
