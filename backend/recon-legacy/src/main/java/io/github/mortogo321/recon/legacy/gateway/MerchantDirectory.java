package io.github.mortogo321.recon.legacy.gateway;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.legacy.dto.MerchantRow;
import io.github.mortogo321.recon.legacy.mapper.MerchantMapper;

/**
 * Merchant master reads. Cached because the batch enrichment step touches the same few thousand
 * merchants for every chunk, and the legacy box is shared with the core banking workload — the
 * cheapest possible query there is the one we never send.
 */
@Service
public class MerchantDirectory {

    public static final String CACHE = "merchants";

    private final MerchantMapper mapper;

    public MerchantDirectory(MerchantMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Cacheable(cacheNames = CACHE, key = "#merchantId", unless = "#result == null")
    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public MerchantRow find(String merchantId) {
        return mapper.findById(merchantId);
    }

    public Optional<MerchantRow> findOptional(String merchantId) {
        return Optional.ofNullable(find(merchantId));
    }

    /**
     * Bulk variant used by the batch processor: one {@code IN (...)} round trip per chunk instead
     * of one query per row. The per-id cache is intentionally bypassed here — filling it row by
     * row from a bulk read would cost more than it saves.
     */
    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public Map<String, MerchantRow> findAll(Collection<String> merchantIds) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            return Map.of();
        }
        return mapper.findAllById(merchantIds).stream()
                .collect(Collectors.toMap(MerchantRow::merchantId, Function.identity(), (a, b) -> a));
    }

    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public List<MerchantRow> search(String nameLike, String mcc, String acquirerId, boolean activeOnly, int limit) {
        return mapper.search(nameLike, mcc, acquirerId, activeOnly, Math.clamp(limit, 1, 200));
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void evictAll() {
        // Called from the admin endpoint after a merchant master refresh on the legacy side.
    }
}
