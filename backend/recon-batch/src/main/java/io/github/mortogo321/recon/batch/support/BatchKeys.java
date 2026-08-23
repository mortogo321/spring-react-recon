package io.github.mortogo321.recon.batch.support;

/**
 * Execution-context keys. Centralised because they are effectively a serialisation contract:
 * a running job restarted after a deployment must still understand the context it saved.
 */
public final class BatchKeys {

    public static final String JOB_NAME = "reconciliationJob";
    public static final String WORKER_STEP = "reconcileMerchantStep";
    public static final String MANAGER_STEP = "reconcilePartitionedStep";

    public static final String PARAM_BUSINESS_DATE = "businessDate";
    public static final String PARAM_TOLERANCE_PROFILE = "toleranceProfile";
    public static final String PARAM_RUN_ID = "runId";
    /** Distinguishes a deliberate re-run of a date from a restart of the attempt that failed. */
    public static final String PARAM_ATTEMPT = "attempt";

    public static final String CTX_RUN_ID = "recon.runId";
    public static final String CTX_MERCHANT_ID = "recon.merchantId";
    public static final String CTX_EXPECTED_ROWS = "recon.expectedRows";
    public static final String CTX_READ_CURSOR = "recon.readCursor";

    public static final String SUM_SETTLEMENT_ROWS = "recon.sum.settlementRows";
    public static final String SUM_LEDGER_ROWS = "recon.sum.ledgerRows";
    public static final String SUM_EXCLUDED_ROWS = "recon.sum.excludedRows";
    public static final String SUM_MATCHED_AMOUNT = "recon.sum.matchedAmount";
    public static final String SUM_EXPOSURE_AMOUNT = "recon.sum.exposureAmount";
    public static final String SUM_CURRENCY = "recon.sum.currency";
    public static final String SUM_COUNT_PREFIX = "recon.sum.count.";

    private BatchKeys() {}
}
