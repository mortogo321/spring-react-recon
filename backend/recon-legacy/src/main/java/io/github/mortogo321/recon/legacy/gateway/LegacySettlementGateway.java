package io.github.mortogo321.recon.legacy.gateway;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.domain.model.SettlementRecord;
import io.github.mortogo321.recon.legacy.dto.MerchantShard;
import io.github.mortogo321.recon.legacy.dto.SettlementTxnRow;
import io.github.mortogo321.recon.legacy.mapper.SettlementTxnMapper;

/**
 * The only way the rest of the application is allowed to reach the legacy Oracle system.
 * Everything crossing this boundary is a domain type, so no MyBatis or JDBC concept leaks upwards
 * (enforced by an ArchUnit rule).
 */
@Service
public class LegacySettlementGateway {

    private final SettlementTxnMapper mapper;

    public LegacySettlementGateway(SettlementTxnMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public long countFor(LocalDate settledOn) {
        return mapper.countBySettlementDate(settledOn);
    }

    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public List<MerchantShard> shardsFor(LocalDate settledOn) {
        return mapper.selectMerchantShards(settledOn);
    }

    /**
     * One page of rows for a merchant, seeking past {@code afterStgId}.
     *
     * <p>Returns the cursor alongside the records rather than letting the caller derive it. The
     * cursor is a staging-table surrogate key — an infrastructure detail that has no business
     * meaning and must not appear on a domain record, but the caller still needs it to ask for the
     * next page.
     */
    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public SettlementPage pageAfter(LocalDate settledOn, String merchantId, Long afterStgId, int limit) {
        List<SettlementTxnRow> rows = mapper.selectPageAfter(settledOn, merchantId, afterStgId, limit);
        return new SettlementPage(
                rows.stream().map(SettlementTxnRow::toDomain).toList(),
                rows.isEmpty() ? afterStgId : rows.getLast().getStgId());
    }

    /** A page of settlement records plus the keyset cursor to resume from. */
    public record SettlementPage(List<SettlementRecord> records, Long nextCursor) {
        public boolean isEmpty() {
            return records.isEmpty();
        }

        public int size() {
            return records.size();
        }
    }

    /**
     * Streams a merchant's rows without materialising them. The cursor is only valid for the
     * duration of the surrounding transaction, hence {@code REQUIRED} rather than a new one.
     */
    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true, propagation = Propagation.REQUIRED)
    public <R> R stream(
            LocalDate settledOn, String merchantId, java.util.function.Function<Cursor<SettlementTxnRow>, R> consumer) {
        try (Cursor<SettlementTxnRow> cursor = mapper.streamByMerchant(settledOn, merchantId)) {
            return consumer.apply(cursor);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to close legacy settlement cursor", e);
        }
    }

    @Transactional(transactionManager = "legacyTransactionManager", readOnly = true)
    public List<SettlementRecord> duplicateCandidates(LocalDate settledOn) {
        return mapper.selectDuplicateCandidates(settledOn).stream()
                .map(SettlementTxnRow::toDomain)
                .toList();
    }
}
