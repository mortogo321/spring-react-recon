package io.github.mortogo321.recon.batch.partition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import io.github.mortogo321.recon.batch.support.BatchKeys;
import io.github.mortogo321.recon.core.service.LedgerQueryService;
import io.github.mortogo321.recon.legacy.dto.MerchantShard;
import io.github.mortogo321.recon.legacy.gateway.LegacySettlementGateway;

/**
 * Splits a business date into per-merchant partitions.
 *
 * <p>Two things here are not obvious and both come from production pain:
 *
 * <ol>
 *   <li>The merchant list is the <em>union</em> of merchants with settlement activity and merchants
 *       with ledger activity. Partitioning on the settlement feed alone silently skips every ledger
 *       posting the acquirer never reported — which is the most expensive class of break to miss.
 *   <li>Partitions are packed largest-first by row count rather than split evenly by merchant, so
 *       one merchant with 40% of the day's volume does not leave the other workers idle while a
 *       single thread finishes the tail.
 * </ol>
 */
public class MerchantShardPartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(MerchantShardPartitioner.class);

    private final LegacySettlementGateway legacy;
    private final LedgerQueryService ledger;
    private final LocalDate businessDate;
    private final Long runId;

    public MerchantShardPartitioner(
            LegacySettlementGateway legacy, LedgerQueryService ledger, LocalDate businessDate, Long runId) {
        this.legacy = legacy;
        this.ledger = ledger;
        this.businessDate = businessDate;
        this.runId = runId;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<MerchantShard> shards = new ArrayList<>(legacy.shardsFor(businessDate));
        Set<String> known = new HashSet<>();
        shards.forEach(s -> known.add(s.merchantId()));

        // Ledger-only merchants: zero settlement rows, but their postings still have to be classified.
        ledger.merchantsWithLedgerActivity(businessDate).stream()
                .filter(id -> !known.contains(id))
                .forEach(id -> shards.add(new MerchantShard(id, 0)));

        if (shards.isEmpty()) {
            log.warn("No settlement or ledger activity for {}; job will complete with an empty run", businessDate);
            return Map.of();
        }

        shards.sort(Comparator.comparingLong(MerchantShard::rowCount).reversed()
                .thenComparing(MerchantShard::merchantId));

        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        for (int i = 0; i < shards.size(); i++) {
            MerchantShard shard = shards.get(i);
            ExecutionContext context = new ExecutionContext();
            context.putString(BatchKeys.CTX_MERCHANT_ID, shard.merchantId());
            context.putLong(BatchKeys.CTX_EXPECTED_ROWS, shard.rowCount());
            context.putLong(BatchKeys.CTX_RUN_ID, runId);
            partitions.put("merchant-" + i + "-" + shard.merchantId(), context);
        }
        log.info(
                "Partitioned {} into {} merchant shards (grid size hint {}), largest {} rows",
                businessDate,
                partitions.size(),
                gridSize,
                shards.getFirst().rowCount());
        return partitions;
    }
}
