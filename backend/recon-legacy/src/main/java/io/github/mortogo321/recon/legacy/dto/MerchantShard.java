package io.github.mortogo321.recon.legacy.dto;

/**
 * One unit of work for the partitioned reconciliation step: a merchant plus the row count the
 * legacy side expects to deliver. The count is what lets the partitioner build balanced shards
 * instead of naively splitting on merchant id and starving most workers.
 */
public record MerchantShard(String merchantId, long rowCount) {}
