package io.github.mortogo321.recon.domain.model;

import java.time.LocalDate;
import java.util.Objects;

import io.github.mortogo321.recon.domain.money.Money;

/** One posting in our own ledger — the side we control and can correct. */
public record LedgerEntry(
        String entryId,
        String merchantId,
        String externalRef,
        Money amount,
        LocalDate postedOn,
        boolean voided) {

    public LedgerEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(postedOn, "postedOn");
    }

    public MatchKey matchKey() {
        return new MatchKey(merchantId, externalRef);
    }

    public boolean isReconcilable() {
        return !voided;
    }
}
