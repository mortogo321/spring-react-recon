package io.github.mortogo321.recon.api.controller;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.mortogo321.recon.api.dto.CommonDtos;
import io.github.mortogo321.recon.api.dto.RunDtos;
import io.github.mortogo321.recon.api.security.ReconRoles;
import io.github.mortogo321.recon.batch.service.ReconJobOperations;
import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.repository.ReconExceptionRepository;
import io.github.mortogo321.recon.core.service.ExceptionWorkflowService;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.core.service.ToleranceProfileRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reconciliation runs and the batch operations behind them.
 *
 * <p>Note the split in authorisation: anyone with a console role can read runs, but only ADMIN can
 * launch, stop, restart or recover one. Re-running a reconciliation is an operational act with real
 * cost against the legacy system, not a report refresh.
 */
@RestController
@RequestMapping("/api/runs")
@Tag(name = "Reconciliation runs")
public class ReconRunController {

    private final ReconRunService runService;
    private final ReconJobOperations jobs;
    private final ExceptionWorkflowService workflow;
    private final ToleranceProfileRegistry tolerances;

    public ReconRunController(
            ReconRunService runService,
            ReconJobOperations jobs,
            ExceptionWorkflowService workflow,
            ToleranceProfileRegistry tolerances) {
        this.runService = runService;
        this.jobs = jobs;
        this.workflow = workflow;
        this.tolerances = tolerances;
    }

    @GetMapping
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Most recent reconciliation runs, newest first")
    public List<RunDtos.RunView> list(
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return runService.recentRuns(limit).stream().map(RunDtos.RunView::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "One run")
    public ResponseEntity<RunDtos.RunView> get(@PathVariable Long id) {
        return runService
                .findById(id)
                .map(RunDtos.RunView::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/profiles")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Configured tolerance profiles")
    public List<String> profiles() {
        return tolerances.availableProfiles().stream().sorted().toList();
    }

    @GetMapping("/{id}/breakdown")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Exception counts and exposure for a run, grouped for the dashboard")
    public RunDtos.RunBreakdown breakdown(@PathVariable Long id) {
        List<ReconExceptionRepository.StatusBreakdownRow> rows = workflow.breakdown(id);

        List<CommonDtos.AmountByName> byStatus = rows.stream()
                .map(row -> new CommonDtos.AmountByName(row.getStatus().name(), row.getTotal(), row.getExposure()))
                .sorted(Comparator.comparing(CommonDtos.AmountByName::name))
                .toList();

        // The same rows regrouped by severity: the grid filters on status, the KPI strip on severity.
        List<CommonDtos.CountByName> bySeverity = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getSeverity().name(), Collectors.summingLong(row -> row.getTotal())))
                .entrySet()
                .stream()
                .map(entry -> new CommonDtos.CountByName(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CommonDtos.CountByName::name))
                .toList();

        List<CommonDtos.CountByName> byState = workflow.stateCounts(id).stream()
                .map(row -> new CommonDtos.CountByName(row.getState().name(), row.getTotal()))
                .sorted(Comparator.comparing(CommonDtos.CountByName::name))
                .toList();

        return new RunDtos.RunBreakdown(id, byStatus, bySeverity, byState, workflow.openCount(id));
    }

    @PostMapping
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Launch a reconciliation for a business date")
    public RunDtos.LaunchResponse launch(@Valid @RequestBody RunDtos.LaunchRequest request) {
        // Unknown profile is a 400 before anything is created, not a job that dies three steps in.
        String profile = tolerances.effectiveProfile(request.toleranceProfile());
        tolerances.resolve(profile);

        // The run row is opened here rather than left to the job's first step, for two reasons: the
        // job starts on a virtual thread, so the caller would be racing that step for the very id
        // it is being told to poll; and the row then records the authenticated operator who
        // triggered it instead of whatever the batch thread's security context happens to hold.
        // openRun is idempotent on the run key, so the step re-attaches to this row - which is also
        // what makes a restart re-use the run instead of orphaning it.
        ReconRunEntity run = runService.openRun(request.businessDate(), profile);
        ReconJobOperations.JobHandle execution;
        try {
            execution = jobs.launch(request.businessDate(), profile);
        } catch (RuntimeException e) {
            // Without this the row sits in PENDING for ever and the console shows a run that never ran.
            runService.fail(run.getId(), "Job failed to start: " + e.getMessage());
            throw e;
        }
        return new RunDtos.LaunchResponse(
                run.getId(), execution.executionId(), execution.status(), run.getRunKey());
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Request a graceful stop of the run's job execution")
    public RunDtos.JobOperationResponse stop(@PathVariable Long id) {
        Long executionId = requireExecutionId(id);
        boolean stopped = jobs.stop(executionId);
        return new RunDtos.JobOperationResponse(
                "stop", stopped, stopped ? "Stop signal delivered" : "Execution was not running", executionId);
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Restart a failed or stopped run from its last restart point")
    public RunDtos.JobOperationResponse restart(@PathVariable Long id) {
        Long executionId = requireExecutionId(id);
        Long newExecution = jobs.restart(executionId);
        return new RunDtos.JobOperationResponse("restart", true, "Restarted", newExecution);
    }

    @PostMapping("/{id}/recover")
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Recover a run left RUNNING by an abrupt shutdown")
    public RunDtos.JobOperationResponse recover(@PathVariable Long id) {
        Long executionId = requireExecutionId(id);
        var execution = jobs.recover(executionId);
        return new RunDtos.JobOperationResponse(
                "recover", true, "Marked " + execution.status(), execution.executionId());
    }

    @PostMapping("/{id}/abandon")
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Abandon a run so the business date can be launched again")
    public RunDtos.JobOperationResponse abandon(@PathVariable Long id) {
        Long executionId = requireExecutionId(id);
        var execution = jobs.abandon(executionId);
        return new RunDtos.JobOperationResponse(
                "abandon", true, "Marked " + execution.status(), execution.executionId());
    }

    @GetMapping("/{id}/steps")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Per-step progress of the run's job execution, partitions included")
    public List<RunDtos.StepView> steps(@PathVariable Long id) {
        return jobs.stepDetails(requireExecutionId(id)).stream()
                .map(step -> new RunDtos.StepView(
                        step.stepExecutionId(),
                        step.name(),
                        step.status(),
                        step.exitCode(),
                        step.readCount(),
                        step.writeCount(),
                        step.filterCount(),
                        step.skipCount(),
                        step.commitCount(),
                        step.rollbackCount(),
                        step.startTime(),
                        step.endTime()))
                .toList();
    }

    private Long requireExecutionId(Long runId) {
        return runService
                .findById(runId)
                .map(ReconRunEntity::getJobExecutionId)
                .orElseThrow(() -> new ReconRunService.ReconRunNotFoundException(runId));
    }
}
