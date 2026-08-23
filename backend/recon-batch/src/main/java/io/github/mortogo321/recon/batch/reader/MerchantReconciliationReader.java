package io.github.mortogo321.recon.batch.reader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.batch.support.ReconCandidate;
import io.github.mortogo321.recon.core.service.LedgerQueryService;
import io.github.mortogo321.recon.domain.model.LedgerEntry;
import io.github.mortogo321.recon.domain.model.MatchKey;
import io.github.mortogo321.recon.domain.model.SettlementRecord;
import io.github.mortogo321.recon.legacy.gateway.LegacySettlementGateway;

/**
 * Reads one merchant's day from both systems and emits a {@link ReconCandidate} per match key.
 *
 * <p>Restartability is the interesting part. The reader records the index of the last key it
 * handed out in the step's {@link ExecutionContext}; on restart it rebuilds the same deterministic
 * key ordering and skips forward. That determinism is why the union of keys is held in a
 * {@link TreeSet} rather than a hash set — a restart that reordered the keys would re-process some
 * and silently drop others.
 *
 * <p>Settlement rows are pulled with keyset pagination so a merchant with millions of rows does
 * not have to fit in one query, while the ledger side is read in a single indexed lookup because
 * per-merchant-day ledger volume is bounded by construction.
 */
public class MerchantReconciliationReader implements ItemStreamReader<ReconCandidate> {

    private static final Logger log = LoggerFactory.getLogger(MerchantReconciliationReader.class);

    private final LegacySettlementGateway legacy;
    private final LedgerQueryService ledger;
    private final LocalDate businessDate;
    private final String merchantId;
    private final int pageSize;

    private List<MatchKey> orderedKeys = List.of();
    private Map<MatchKey, List<SettlementRecord>> settlementsByKey = Map.of();
    private Map<MatchKey, List<LedgerEntry>> ledgerByKey = Map.of();
    private int cursor;
    private long settlementRowsRead;
    private long ledgerRowsRead;
    private long excludedRowsRead;

    public MerchantReconciliationReader(
            LegacySettlementGateway legacy,
            LedgerQueryService ledger,
            LocalDate businessDate,
            String merchantId,
            int pageSize) {
        this.legacy = legacy;
        this.ledger = ledger;
        this.businessDate = businessDate;
        this.merchantId = merchantId;
        this.pageSize = pageSize;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        List<SettlementRecord> settlements = readAllSettlements();
        List<LedgerEntry> ledgerEntries = ledger.entriesFor(businessDate, merchantId);
        settlementRowsRead = settlements.size();
        ledgerRowsRead = ledgerEntries.size();
        // Reversals, chargebacks, rejects and still-pending rows are in the feed but out of scope.
        // The engine drops them per key; the count has to be captured here, on the raw read, or the
        // run reports "0 excluded" and an operator cannot tell a quiet day from a filtered one.
        excludedRowsRead = settlements.stream()
                .filter(settlement -> !settlement.isReconcilable())
                .count();

        settlementsByKey = settlements.stream()
                .collect(Collectors.groupingBy(SettlementRecord::matchKey, LinkedHashMap::new, Collectors.toList()));
        ledgerByKey = ledgerEntries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::matchKey, LinkedHashMap::new, Collectors.toList()));

        TreeSet<MatchKey> union = new TreeSet<>(settlementsByKey.keySet());
        union.addAll(ledgerByKey.keySet());
        orderedKeys = List.copyOf(union);

        cursor = (int) executionContext.getLong(BatchKeys.CTX_READ_CURSOR, 0);
        if (cursor > 0) {
            log.info("Resuming merchant {} for {} at key index {}/{}", merchantId, businessDate, cursor, orderedKeys.size());
        } else {
            log.debug(
                    "Merchant {} on {}: {} settlement rows, {} ledger rows, {} keys",
                    merchantId,
                    businessDate,
                    settlementRowsRead,
                    ledgerRowsRead,
                    orderedKeys.size());
        }
    }

    @Override
    public ReconCandidate read() {
        if (cursor >= orderedKeys.size()) {
            return null;
        }
        MatchKey key = orderedKeys.get(cursor++);
        return new ReconCandidate(
                key,
                settlementsByKey.getOrDefault(key, List.of()),
                ledgerByKey.getOrDefault(key, List.of()));
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(BatchKeys.CTX_READ_CURSOR, cursor);
    }

    @Override
    public void close() throws ItemStreamException {
        orderedKeys = List.of();
        settlementsByKey = Map.of();
        ledgerByKey = Map.of();
    }

    public long settlementRowsRead() {
        return settlementRowsRead;
    }

    public long excludedRowsRead() {
        return excludedRowsRead;
    }

    public long ledgerRowsRead() {
        return ledgerRowsRead;
    }

    /** Keyset pagination over the legacy feed: seek past the last surrogate id, never OFFSET. */
    private List<SettlementRecord> readAllSettlements() {
        List<SettlementRecord> all = new ArrayList<>();
        Long cursor = null;
        while (true) {
            var page = legacy.pageAfter(businessDate, merchantId, cursor, pageSize);
            if (page.isEmpty()) {
                return all;
            }
            all.addAll(page.records());
            cursor = page.nextCursor();
            if (page.size() < pageSize) {
                return all;
            }
        }
    }
}
