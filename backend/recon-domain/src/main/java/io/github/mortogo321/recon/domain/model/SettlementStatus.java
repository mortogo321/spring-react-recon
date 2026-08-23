package io.github.mortogo321.recon.domain.model;

/** Status codes as they appear in the legacy feed, mapped from single-character Oracle columns. */
public enum SettlementStatus {
    SETTLED('S', true),
    PENDING('P', false),
    REVERSED('R', false),
    CHARGEBACK('C', false),
    REJECTED('X', false);

    private final char code;
    private final boolean reconcilable;

    SettlementStatus(char code, boolean reconcilable) {
        this.code = code;
        this.reconcilable = reconcilable;
    }

    public char code() {
        return code;
    }

    public boolean reconcilable() {
        return reconcilable;
    }

    public static SettlementStatus fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("settlement status code must not be blank");
        }
        char c = Character.toUpperCase(raw.trim().charAt(0));
        for (SettlementStatus status : values()) {
            if (status.code == c) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown settlement status code: " + raw);
    }
}
