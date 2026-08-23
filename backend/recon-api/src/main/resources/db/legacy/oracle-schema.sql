-- Stand-in for the legacy core-banking Oracle schema.
-- Used only by the local profile, where H2 runs in Oracle compatibility mode. Against a real
-- Oracle instance this script is never executed: we have read-only access and do not own the DDL.

CREATE TABLE MERCHANT_MASTER (
    MERCHANT_ID         VARCHAR2(32)  NOT NULL,
    LEGAL_NAME          VARCHAR2(200) NOT NULL,
    MCC                 VARCHAR2(4),
    SETTLEMENT_CURRENCY VARCHAR2(3)   NOT NULL,
    ACQUIRER_ID         VARCHAR2(16)  NOT NULL,
    ONBOARDED_ON        DATE          NOT NULL,
    ACTIVE_FLAG         CHAR(1)       DEFAULT 'Y' NOT NULL,
    CONSTRAINT PK_MERCHANT_MASTER PRIMARY KEY (MERCHANT_ID)
);

CREATE TABLE STG_SETTLEMENT_TXN (
    -- Surrogate key. TXN_ID is deliberately NOT unique: when an acquirer re-delivers a file the
    -- same transaction arrives twice, and the staging table has to be able to hold both so the
    -- reconciliation can report the duplicate instead of the load failing.
    STG_ID            NUMBER(19)    NOT NULL,
    TXN_ID            VARCHAR2(64)  NOT NULL,
    MERCHANT_ID       VARCHAR2(32)  NOT NULL,
    EXTERNAL_REF      VARCHAR2(64)  NOT NULL,
    GROSS_AMOUNT      NUMBER(19, 4) NOT NULL,
    FEE_AMOUNT        NUMBER(19, 4) DEFAULT 0 NOT NULL,
    CURRENCY_CODE     VARCHAR2(3)   NOT NULL,
    SETTLED_ON        DATE          NOT NULL,
    STATUS_CODE       CHAR(1)       NOT NULL,
    ACQUIRER_BATCH_ID VARCHAR2(32),
    CONSTRAINT PK_STG_SETTLEMENT_TXN PRIMARY KEY (STG_ID)
);

-- Mirrors the indexes the real system has; without the first one the per-merchant cursor read
-- degrades into a full scan of the staging table once per partition.
CREATE INDEX IX_STG_SETTLE_DATE_MERCH ON STG_SETTLEMENT_TXN (SETTLED_ON, MERCHANT_ID, STG_ID);
CREATE INDEX IX_STG_SETTLE_TXN ON STG_SETTLEMENT_TXN (TXN_ID);
CREATE INDEX IX_STG_SETTLE_DUP ON STG_SETTLEMENT_TXN (SETTLED_ON, MERCHANT_ID, EXTERNAL_REF, GROSS_AMOUNT);
