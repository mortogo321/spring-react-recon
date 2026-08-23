package io.github.mortogo321.recon.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.mortogo321.recon.api.dto.CommonDtos.MoneyDto;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Kpis(
            LocalDate latestBusinessDate,
            BigDecimal matchRate,
            long exceptionKeys,
            long openExceptions,
            MoneyDto exposure,
            long settlementRows,
            boolean hasCriticalBreaks) {}

    public record TrendPoint(LocalDate businessDate, BigDecimal matchRate, long exceptionKeys, long settlementRows) {}

    public record Dashboard(
            Kpis kpis,
            List<TrendPoint> trend,
            List<CommonDtos.AmountByName> exceptionsByStatus,
            List<CommonDtos.CountByName> exceptionsByState,
            List<RunDtos.RunView> recentRuns) {}
}
