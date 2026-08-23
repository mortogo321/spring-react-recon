package io.github.mortogo321.recon.legacy.dto;

import java.time.LocalDate;

/** Merchant master data, read-through cached because it changes far more slowly than the feed. */
public record MerchantRow(
        String merchantId,
        String legalName,
        String mcc,
        String settlementCurrency,
        String acquirerId,
        LocalDate onboardedOn,
        boolean active) {}
