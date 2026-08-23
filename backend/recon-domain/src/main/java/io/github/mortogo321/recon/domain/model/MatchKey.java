package io.github.mortogo321.recon.domain.model;

import java.util.Objects;

/**
 * Natural join key between the acquirer settlement feed and our internal ledger.
 * Both sides agree on (merchant, external reference); nothing else is trustworthy across systems.
 */
public record MatchKey(String merchantId, String externalRef) implements Comparable<MatchKey> {

    public MatchKey {
        merchantId = requireText(merchantId, "merchantId");
        externalRef = requireText(externalRef, "externalRef");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    @Override
    public int compareTo(MatchKey other) {
        int byMerchant = merchantId.compareTo(other.merchantId);
        return byMerchant != 0 ? byMerchant : externalRef.compareTo(other.externalRef);
    }

    @Override
    public String toString() {
        return merchantId + "/" + externalRef;
    }
}
