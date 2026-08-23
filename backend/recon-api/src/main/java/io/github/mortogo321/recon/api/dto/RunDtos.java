package io.github.mortogo321.recon.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import io.github.mortogo321.recon.api.dto.CommonDtos.MoneyDto;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.entity.RunStatus;

public final class RunDtos {

    private RunDtos() {}

    public record LaunchRequest(
            @NotNull @PastOrPresent LocalDate businessDate, @Size(max = 64) String toleranceProfile) {}

    public record RunView(
            Long id,
            String runKey,
            LocalDate businessDate,
            RunStatus status,
            String toleranceProfile,
            String triggeredBy,
            Long jobExecutionId,
            Instant startedAt,
            Instant finishedAt,
            long settlementRows,
            long ledgerRows,
            long excludedRows,
            long matchedKeys,
            long exceptionKeys,
            BigDecimal matchRate,
            MoneyDto matchedAmount,
            MoneyDto exposure,
            String failureReason,
            boolean restartable) {

        public static RunView of(ReconRunEntity run) {
            return new RunView(
                    run.getId(),
                    run.getRunKey(),
                    run.getBusinessDate(),
                    run.getStatus(),
                    run.getToleranceProfile(),
                    run.getTriggeredBy(),
                    run.getJobExecutionId(),
                    run.getStartedAt(),
                    run.getFinishedAt(),
                    run.getSettlementRows(),
                    run.getLedgerRows(),
                    run.getExcludedRows(),
                    run.getMatchedKeys(),
                    run.getExceptionKeys(),
                    run.getMatchRate(),
                    MoneyDto.of(run.getMatchedAmount()),
                    MoneyDto.of(run.getExposure()),
                    run.getFailureReason(),
                    run.getStatus().isRestartable());
        }
    }

    public record LaunchResponse(Long runId, Long jobExecutionId, String status, String runKey) {}

    public record JobOperationResponse(String operation, boolean accepted, String detail, Long jobExecutionId) {}

    /**
     * One row of the run's step table. The partition steps carry the shard in their name
     * ({@code reconcileMerchantStep:merchant-2-M-1003}), which is what makes a slow or skipping
     * merchant visible without opening the logs.
     */
    public record StepView(
            Long stepExecutionId,
            String name,
            String status,
            String exitCode,
            long readCount,
            long writeCount,
            long filterCount,
            long skipCount,
            long commitCount,
            long rollbackCount,
            LocalDateTime startTime,
            LocalDateTime endTime) {}

    public record RunBreakdown(
            Long runId,
            List<CommonDtos.AmountByName> byStatus,
            List<CommonDtos.CountByName> bySeverity,
            List<CommonDtos.CountByName> byState,
            long openCount) {}
}
