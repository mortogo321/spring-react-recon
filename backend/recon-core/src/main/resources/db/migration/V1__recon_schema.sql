-- Application-owned MySQL schema. Flyway is the only thing that changes it; Hibernate runs with
-- ddl-auto disabled so a stray entity edit can never silently alter production.

CREATE TABLE recon_run (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    version           BIGINT       NOT NULL DEFAULT 0,
    run_key           VARCHAR(100) NOT NULL,
    business_date     DATE         NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    job_execution_id  BIGINT       NULL,
    tolerance_profile VARCHAR(64)  NOT NULL,
    triggered_by      VARCHAR(64)  NULL,
    started_at        DATETIME(6)  NULL,
    finished_at       DATETIME(6)  NULL,
    settlement_rows   BIGINT       NOT NULL DEFAULT 0,
    ledger_rows       BIGINT       NOT NULL DEFAULT 0,
    excluded_rows     BIGINT       NOT NULL DEFAULT 0,
    matched_keys      BIGINT       NOT NULL DEFAULT 0,
    exception_keys    BIGINT       NOT NULL DEFAULT 0,
    match_rate        DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    matched_amount    DECIMAL(19,4) NULL,
    matched_currency  VARCHAR(3)   NULL,
    exposure_amount   DECIMAL(19,4) NULL,
    exposure_currency VARCHAR(3)   NULL,
    failure_reason    VARCHAR(1024) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(64)  NULL,
    updated_by        VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recon_run_key UNIQUE (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_recon_run_business_date ON recon_run (business_date);
CREATE INDEX ix_recon_run_status ON recon_run (status);

CREATE TABLE recon_exception (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    version           BIGINT        NOT NULL DEFAULT 0,
    run_id            BIGINT        NOT NULL,
    merchant_id       VARCHAR(32)   NOT NULL,
    external_ref      VARCHAR(64)   NOT NULL,
    status            VARCHAR(32)   NOT NULL,
    severity          VARCHAR(16)   NOT NULL,
    state             VARCHAR(24)   NOT NULL,
    settlement_amount DECIMAL(19,4) NULL,
    settlement_currency VARCHAR(3)  NULL,
    ledger_amount     DECIMAL(19,4) NULL,
    ledger_currency   VARCHAR(3)    NULL,
    exposure_amount   DECIMAL(19,4) NULL,
    exposure_currency VARCHAR(3)    NULL,
    detail            VARCHAR(512)  NOT NULL,
    assigned_to       VARCHAR(64)   NULL,
    resolution_note   VARCHAR(1024) NULL,
    submitted_by      VARCHAR(64)   NULL,
    submitted_at      DATETIME(6)   NULL,
    decided_by        VARCHAR(64)   NULL,
    decided_at        DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(64)   NULL,
    updated_by        VARCHAR(64)   NULL,
    PRIMARY KEY (id),
    -- Natural key: a re-run or a restart converges on the same row instead of duplicating the queue.
    CONSTRAINT uk_recon_exception_natural UNIQUE (run_id, merchant_id, external_ref, status),
    CONSTRAINT fk_exception_run FOREIGN KEY (run_id) REFERENCES recon_run (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_recon_exception_run_state ON recon_exception (run_id, state);
-- Covers the console's default sort: worst severity first, biggest money first.
CREATE INDEX ix_recon_exception_severity ON recon_exception (severity, exposure_amount);
CREATE INDEX ix_recon_exception_merchant ON recon_exception (merchant_id);
CREATE INDEX ix_recon_exception_assignee ON recon_exception (assigned_to, state);

CREATE TABLE exception_comment (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    version      BIGINT        NOT NULL DEFAULT 0,
    exception_id BIGINT        NOT NULL,
    author       VARCHAR(64)   NOT NULL,
    body         VARCHAR(2000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_exception FOREIGN KEY (exception_id) REFERENCES recon_exception (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_comment_exception ON exception_comment (exception_id);

CREATE TABLE ledger_entry (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    version      BIGINT        NOT NULL DEFAULT 0,
    entry_id     VARCHAR(64)   NOT NULL,
    merchant_id  VARCHAR(32)   NOT NULL,
    external_ref VARCHAR(64)   NOT NULL,
    amount       DECIMAL(19,4) NULL,
    currency     VARCHAR(3)    NULL,
    posted_on    DATE          NOT NULL,
    voided       BIT(1)        NOT NULL DEFAULT b'0',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_entry_id UNIQUE (entry_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Composite order matters: the batch reads by (date, merchant) and the console filters by ref.
CREATE INDEX ix_ledger_lookup ON ledger_entry (posted_on, merchant_id, external_ref);

CREATE TABLE outbox_event (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        LONGTEXT    NOT NULL,
    status         VARCHAR(16) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    published_at   DATETIME(6) NULL,
    attempts       INT         NOT NULL DEFAULT 0,
    last_error     VARCHAR(1024) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Supports the claim query's ORDER BY under a status filter, so SKIP LOCKED scans no further than it must.
CREATE INDEX ix_outbox_dispatch ON outbox_event (status, occurred_at);
CREATE INDEX ix_outbox_aggregate ON outbox_event (aggregate_type, aggregate_id);
