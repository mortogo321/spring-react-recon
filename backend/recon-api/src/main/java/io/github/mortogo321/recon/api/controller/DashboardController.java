package io.github.mortogo321.recon.api.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.mortogo321.recon.api.dto.CommonDtos;
import io.github.mortogo321.recon.api.dto.DashboardDtos;
import io.github.mortogo321.recon.api.dto.RunDtos;
import io.github.mortogo321.recon.api.security.ReconRoles;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.service.ExceptionWorkflowService;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * One aggregated payload for the landing page.
 *
 * <p>Deliberately a single endpoint rather than five: the dashboard is useless half-loaded, and
 * five parallel round trips from the browser is five chances to render an inconsistent snapshot
 * where the KPI strip and the chart disagree about which run is latest.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final ReconRunService runs;
    private final ExceptionWorkflowService workflow;
    private final Clock clock;

    public DashboardController(ReconRunService runs, ExceptionWorkflowService workflow, Clock clock) {
        this.runs = runs;
        this.workflow = workflow;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "KPIs, trend and recent runs for the console landing page")
    public DashboardDtos.Dashboard dashboard(@RequestParam(defaultValue = "14") @Min(1) @Max(90) int trendDays) {
        List<ReconRunEntity> recent = runs.recentRuns(10);

        List<DashboardDtos.TrendPoint> trend =
                runs.trend(LocalDate.now(clock).minusDays(trendDays)).stream()
                        .map(row -> new DashboardDtos.TrendPoint(
                                row.getBusinessDate(),
                                row.getMatchRate(),
                                row.getExceptionKeys(),
                                row.getSettlementRows()))
                        .toList();

        // The newest run that actually produced numbers; a run still in flight has none yet.
        ReconRunEntity latest = recent.stream()
                .filter(run -> run.getStatus().isTerminal())
                .findFirst()
                .orElse(recent.isEmpty() ? null : recent.getFirst());

        DashboardDtos.Kpis kpis = latest == null
                ? new DashboardDtos.Kpis(null, BigDecimal.ZERO, 0, 0, null, 0, false)
                : new DashboardDtos.Kpis(
                        latest.getBusinessDate(),
                        latest.getMatchRate(),
                        latest.getExceptionKeys(),
                        workflow.openCount(latest.getId()),
                        CommonDtos.MoneyDto.of(latest.getExposure()),
                        latest.getSettlementRows(),
                        latest.getExceptionKeys() > 0);

        List<CommonDtos.AmountByName> byStatus = latest == null
                ? List.of()
                : workflow.breakdown(latest.getId()).stream()
                        .map(row -> new CommonDtos.AmountByName(
                                row.getStatus().name(), row.getTotal(), row.getExposure()))
                        .sorted(Comparator.comparing(CommonDtos.AmountByName::name))
                        .toList();

        List<CommonDtos.CountByName> byState = latest == null
                ? List.of()
                : workflow.stateCounts(latest.getId()).stream()
                        .map(row -> new CommonDtos.CountByName(row.getState().name(), row.getTotal()))
                        .sorted(Comparator.comparing(CommonDtos.CountByName::name))
                        .collect(Collectors.toList());

        return new DashboardDtos.Dashboard(
                kpis, trend, byStatus, byState, recent.stream().map(RunDtos.RunView::of).toList());
    }
}
