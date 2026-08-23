package io.github.mortogo321.recon.legacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import io.github.mortogo321.recon.domain.model.SettlementRecord;
import io.github.mortogo321.recon.domain.model.SettlementStatus;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * Straight projection of one {@code STG_SETTLEMENT_TXN} row. Mutable with a no-arg constructor
 * because MyBatis populates it by setter; it is converted to the immutable domain record
 * immediately and never escapes this module.
 */
public class SettlementTxnRow {

    private long stgId;
    private String txnId;
    private String merchantId;
    private String externalRef;
    private BigDecimal grossAmount;
    private BigDecimal feeAmount;
    private Currency currency;
    private LocalDate settledOn;
    private SettlementStatus status;
    private String acquirerBatchId;

    public SettlementRecord toDomain() {
        Currency ccy = currency;
        return new SettlementRecord(
                txnId,
                merchantId,
                externalRef,
                new Money(grossAmount, ccy),
                new Money(feeAmount == null ? BigDecimal.ZERO : feeAmount, ccy),
                settledOn,
                status,
                acquirerBatchId);
    }

    public long getStgId() {
        return stgId;
    }

    public void setStgId(long stgId) {
        this.stgId = stgId;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public LocalDate getSettledOn() {
        return settledOn;
    }

    public void setSettledOn(LocalDate settledOn) {
        this.settledOn = settledOn;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public String getAcquirerBatchId() {
        return acquirerBatchId;
    }

    public void setAcquirerBatchId(String acquirerBatchId) {
        this.acquirerBatchId = acquirerBatchId;
    }
}
