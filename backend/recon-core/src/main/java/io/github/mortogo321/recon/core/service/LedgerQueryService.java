package io.github.mortogo321.recon.core.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.core.entity.LedgerEntryEntity;
import io.github.mortogo321.recon.core.repository.LedgerEntryRepository;
import io.github.mortogo321.recon.domain.model.LedgerEntry;

/** Read side of our own ledger, exposed to the batch as domain records. */
@Service
public class LedgerQueryService {

    private final LedgerEntryRepository ledger;

    public LedgerQueryService(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesFor(LocalDate postedOn, String merchantId) {
        return ledger.findByPostedOnAndMerchantId(postedOn, merchantId).stream()
                .map(LedgerEntryEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesFor(LocalDate postedOn, Collection<String> merchantIds) {
        return ledger.findByPostedOnAndMerchantIdIn(postedOn, merchantIds).stream()
                .map(LedgerEntryEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countFor(LocalDate postedOn) {
        return ledger.countByPostedOn(postedOn);
    }

    /**
     * Merchants that only exist on our side for the date. Without these the run would silently
     * ignore ledger postings the acquirer never reported — the most expensive break to miss.
     */
    @Transactional(readOnly = true)
    public List<String> merchantsWithLedgerActivity(LocalDate postedOn) {
        return ledger.findMerchantIdsFor(postedOn);
    }
}
