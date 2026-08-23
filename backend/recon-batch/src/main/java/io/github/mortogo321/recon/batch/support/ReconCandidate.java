package io.github.mortogo321.recon.batch.support;

import java.util.List;
import java.util.Objects;

import io.github.mortogo321.recon.domain.model.LedgerEntry;
import io.github.mortogo321.recon.domain.model.MatchKey;
import io.github.mortogo321.recon.domain.model.SettlementRecord;

/**
 * One unit of reconciliation work: everything both systems know about a single match key.
 *
 * <p>Making the *key* the batch item rather than the *row* is the design decision that makes this
 * job both chunk-oriented and correct. Reconciliation is inherently a set operation — you cannot
 * classify a settlement row without knowing whether a ledger posting exists — so an item has to
 * carry both sides. It also means a chunk failure retries a bounded, self-contained piece of work.
 */
public record ReconCandidate(MatchKey key, List<SettlementRecord> settlements, List<LedgerEntry> ledgerEntries) {

    public ReconCandidate {
        Objects.requireNonNull(key, "key");
        settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        ledgerEntries = List.copyOf(Objects.requireNonNull(ledgerEntries, "ledgerEntries"));
    }

    public int rowCount() {
        return settlements.size() + ledgerEntries.size();
    }
}
