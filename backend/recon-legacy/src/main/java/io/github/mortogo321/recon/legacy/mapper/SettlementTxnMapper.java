package io.github.mortogo321.recon.legacy.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import io.github.mortogo321.recon.legacy.dto.MerchantShard;
import io.github.mortogo321.recon.legacy.dto.SettlementTxnRow;

/**
 * Read access to the acquirer settlement staging table. Statements live in XML rather than
 * annotations because several of them are non-trivial Oracle SQL that a DBA needs to be able to
 * read, explain-plan and index without opening the Java source.
 */
@Mapper
public interface SettlementTxnMapper {

    /** Total in-scope rows for a business date — used for the run's expected-volume check. */
    long countBySettlementDate(@Param("settledOn") LocalDate settledOn);

    /**
     * Merchants with activity on the date plus their row counts, so the partitioner can build
     * balanced shards rather than assuming an even distribution.
     */
    List<MerchantShard> selectMerchantShards(@Param("settledOn") LocalDate settledOn);

    /**
     * Server-side cursor over one merchant's rows. Must be consumed inside an open session; the
     * batch reader owns that lifecycle. Streaming rather than paging avoids the deep-OFFSET
     * penalty that makes page 5,000 of a large extract cost the same as a full scan.
     */
    Cursor<SettlementTxnRow> streamByMerchant(
            @Param("settledOn") LocalDate settledOn, @Param("merchantId") String merchantId);

    /**
     * Keyset-paged read. Seeks past {@code afterStgId} — the surrogate key, not {@code TXN_ID},
     * because a re-delivered file repeats the business id and a non-unique seek key silently
     * overlaps or skips rows at every page boundary.
     */
    List<SettlementTxnRow> selectPageAfter(
            @Param("settledOn") LocalDate settledOn,
            @Param("merchantId") String merchantId,
            @Param("afterStgId") Long afterStgId,
            @Param("limit") int limit);

    /**
     * Rows whose (merchant, external_ref, amount) tuple repeats within the business date, detected
     * with an analytic function so the whole check is one pass over the staging table.
     */
    List<SettlementTxnRow> selectDuplicateCandidates(@Param("settledOn") LocalDate settledOn);
}
