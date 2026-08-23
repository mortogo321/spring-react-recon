package io.github.mortogo321.recon.api.dto;

import java.time.LocalDate;

import io.github.mortogo321.recon.legacy.dto.MerchantRow;

public final class MerchantDtos {

    private MerchantDtos() {}

    /** Read model over the legacy Oracle merchant master; no legacy type crosses the HTTP boundary. */
    public record MerchantView(
            String merchantId,
            String legalName,
            String mcc,
            String settlementCurrency,
            String acquirerId,
            LocalDate onboardedOn,
            boolean active) {

        public static MerchantView of(MerchantRow row) {
            return new MerchantView(
                    row.merchantId(),
                    row.legalName(),
                    row.mcc(),
                    row.settlementCurrency(),
                    row.acquirerId(),
                    row.onboardedOn(),
                    row.active());
        }
    }
}
