package io.github.mortogo321.recon.domain.model;

import java.time.LocalDate;
import java.util.Objects;

import io.github.mortogo321.recon.domain.money.Money;

/**
 * One row of the acquirer settlement feed, as read from the legacy core-banking system.
 * {@code gross} is what the acquirer says they settled; {@code fee} is deducted by them,
 * so the amount that should reach our ledger is {@link #net()}.
 */
public record SettlementRecord(
        String txnId,
        String merchantId,
        String externalRef,
        Money gross,
        Money fee,
        LocalDate settledOn,
        SettlementStatus status,
        String acquirerBatchId) {

    public SettlementRecord {
        Objects.requireNonNull(txnId, "txnId");
        Objects.requireNonNull(gross, "gross");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(settledOn, "settledOn");
        Objects.requireNonNull(status, "status");
        if (!gross.sameCurrencyAs(fee)) {
            throw new Money.CurrencyMismatchException(gross.currencyCode(), fee.currencyCode());
        }
    }

    public MatchKey matchKey() {
        return new MatchKey(merchantId, externalRef);
    }

    public Money net() {
        return gross.subtract(fee);
    }

    /** Reversals and chargebacks must not be netted into the matched population. */
    public boolean isReconcilable() {
        return status.reconcilable();
    }
}
