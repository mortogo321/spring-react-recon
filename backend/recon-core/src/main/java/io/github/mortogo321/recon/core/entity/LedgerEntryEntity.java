package io.github.mortogo321.recon.core.entity;

import java.time.LocalDate;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.github.mortogo321.recon.domain.model.LedgerEntry;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * Our own ledger postings — the side of the reconciliation we control.
 * The composite index on (posted_on, merchant_id, external_ref) is what makes the batch's
 * per-shard ledger read an index-only scan rather than a table scan per merchant.
 */
@Entity
@Table(
        name = "ledger_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_entry_id", columnNames = "entry_id"),
        indexes = @Index(name = "ix_ledger_lookup", columnList = "posted_on,merchant_id,external_ref"))
public class LedgerEntryEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, length = 64)
    private String entryId;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    private MoneyAmount amount;

    @Column(name = "posted_on", nullable = false)
    private LocalDate postedOn;

    @Column(nullable = false)
    private boolean voided;

    protected LedgerEntryEntity() {
        // for JPA
    }

    public LedgerEntryEntity(
            String entryId, String merchantId, String externalRef, Money amount, LocalDate postedOn, boolean voided) {
        this.entryId = entryId;
        this.merchantId = merchantId;
        this.externalRef = externalRef;
        this.amount = MoneyAmount.from(amount);
        this.postedOn = postedOn;
        this.voided = voided;
    }

    public LedgerEntry toDomain() {
        return new LedgerEntry(entryId, merchantId, externalRef, amount.toMoney(), postedOn, voided);
    }

    public Long getId() {
        return id;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Money getAmount() {
        return amount.toMoney();
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public boolean isVoided() {
        return voided;
    }
}
