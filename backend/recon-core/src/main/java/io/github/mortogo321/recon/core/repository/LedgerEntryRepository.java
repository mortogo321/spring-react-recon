package io.github.mortogo321.recon.core.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mortogo321.recon.core.entity.LedgerEntryEntity;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {

    /** Per-shard read used by the batch step; hits ix_ledger_lookup directly. */
    List<LedgerEntryEntity> findByPostedOnAndMerchantId(LocalDate postedOn, String merchantId);

    List<LedgerEntryEntity> findByPostedOnAndMerchantIdIn(LocalDate postedOn, Collection<String> merchantIds);

    long countByPostedOn(LocalDate postedOn);

    @Query("select distinct l.merchantId from LedgerEntryEntity l where l.postedOn = :postedOn")
    List<String> findMerchantIdsFor(@Param("postedOn") LocalDate postedOn);
}
