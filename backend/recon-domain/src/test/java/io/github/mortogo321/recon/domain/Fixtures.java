package io.github.mortogo321.recon.domain;

import java.time.LocalDate;

import io.github.mortogo321.recon.domain.model.LedgerEntry;
import io.github.mortogo321.recon.domain.model.SettlementRecord;
import io.github.mortogo321.recon.domain.model.SettlementStatus;
import io.github.mortogo321.recon.domain.money.Money;

/** Terse builders so the intent of each reconciliation test stays readable. */
final class Fixtures {

    static final String CCY = "THB";
    static final LocalDate DAY = LocalDate.of(2026, 8, 20);
    static final String MERCHANT = "M-1001";

    private Fixtures() {}

    static Money thb(String amount) {
        return Money.of(amount, CCY);
    }

    static SettlementRecord settlement(String txnId, String ref, String gross, String fee) {
        return new SettlementRecord(
                txnId, MERCHANT, ref, thb(gross), thb(fee), DAY, SettlementStatus.SETTLED, "BATCH-1");
    }

    static SettlementRecord settlement(String txnId, String ref, String gross) {
        return settlement(txnId, ref, gross, "0.00");
    }

    static SettlementRecord settlement(String txnId, String ref, String gross, SettlementStatus status) {
        return new SettlementRecord(
                txnId, MERCHANT, ref, thb(gross), thb("0.00"), DAY, status, "BATCH-1");
    }

    static SettlementRecord settlementIn(String txnId, String ref, String gross, String currency) {
        return new SettlementRecord(
                txnId,
                MERCHANT,
                ref,
                Money.of(gross, currency),
                Money.of("0.00", currency),
                DAY,
                SettlementStatus.SETTLED,
                "BATCH-1");
    }

    static LedgerEntry ledger(String entryId, String ref, String amount) {
        return new LedgerEntry(entryId, MERCHANT, ref, thb(amount), DAY, false);
    }

    static LedgerEntry ledgerIn(String entryId, String ref, String amount, String currency) {
        return new LedgerEntry(entryId, MERCHANT, ref, Money.of(amount, currency), DAY, false);
    }

    static LedgerEntry voidedLedger(String entryId, String ref, String amount) {
        return new LedgerEntry(entryId, MERCHANT, ref, thb(amount), DAY, true);
    }
}
