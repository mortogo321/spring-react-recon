package io.github.mortogo321.recon.api.controller;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.mortogo321.recon.api.dto.CommonDtos;
import io.github.mortogo321.recon.api.dto.ExceptionDtos;
import io.github.mortogo321.recon.api.security.ReconRoles;
import io.github.mortogo321.recon.core.entity.ExceptionState;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.core.repository.ReconExceptionSpecifications;
import io.github.mortogo321.recon.core.service.ExceptionWorkflowService;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The exception workbench.
 *
 * <p>Two read paths exist on purpose. The filtered search is offset-paged because the grid needs a
 * total row count to render its pager. The cursor endpoint is keyset-paged and is what the grid uses
 * to scroll a large run, where deep offsets would get progressively slower. Same data, two access
 * patterns, each paying only for what it needs.
 *
 * <p>Write paths are split by role, which is the maker-checker control expressed in HTTP:
 * an OPERATOR may assign, comment and submit; only an APPROVER may decide.
 */
@RestController
@RequestMapping("/api/exceptions")
@Tag(name = "Exceptions")
public class ReconExceptionController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ExceptionWorkflowService workflow;

    public ReconExceptionController(ExceptionWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Search exceptions with the console's filter set")
    public CommonDtos.PagedResult<ExceptionDtos.ExceptionRow> search(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) List<MatchStatus> status,
            @RequestParam(required = false) List<MatchSeverity> severity,
            @RequestParam(required = false) List<ExceptionState> state,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) BigDecimal minExposure,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "exposure") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Specification<ReconExceptionEntity> spec = ReconExceptionSpecifications.inRun(runId)
                .and(ReconExceptionSpecifications.hasStatus(status))
                .and(ReconExceptionSpecifications.hasSeverity(severity))
                .and(ReconExceptionSpecifications.hasState(state))
                .and(ReconExceptionSpecifications.merchant(merchantId))
                .and(ReconExceptionSpecifications.assignedTo(assignedTo))
                .and(ReconExceptionSpecifications.exposureAtLeast(minExposure))
                .and(ReconExceptionSpecifications.search(q));

        Page<ReconExceptionEntity> result =
                workflow.search(spec, PageRequest.of(page, size, sortOf(sortBy, sortDir)));

        return new CommonDtos.PagedResult<>(
                result.getContent().stream().map(ExceptionDtos.ExceptionRow::of).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/cursor")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Keyset-paged exceptions for a run; pass the previous page's nextCursor")
    public CommonDtos.CursorPage<ExceptionDtos.ExceptionRow> cursor(
            @RequestParam Long runId,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        List<ExceptionDtos.ExceptionRow> rows = workflow.pageByRun(runId, after, limit).stream()
                .map(ExceptionDtos.ExceptionRow::of)
                .toList();
        return CommonDtos.CursorPage.of(rows, limit, ExceptionDtos.ExceptionRow::id);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "One exception with its full comment trail")
    public ExceptionDtos.ExceptionDetail get(@PathVariable Long id) {
        return ExceptionDtos.ExceptionDetail.of(workflow.requireWithComments(id));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(ReconRoles.HAS_OPERATOR + " or " + ReconRoles.HAS_APPROVER)
    @Operation(summary = "Assign an exception and move it to INVESTIGATING")
    public ExceptionDtos.ExceptionRow assign(
            @PathVariable Long id, @Valid @RequestBody ExceptionDtos.AssignRequest request) {
        return ExceptionDtos.ExceptionRow.of(workflow.assign(id, request.assignee()));
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize(ReconRoles.HAS_OPERATOR + " or " + ReconRoles.HAS_APPROVER)
    @Operation(summary = "Assign many exceptions in one statement")
    public ExceptionDtos.BulkAssignResponse bulkAssign(@Valid @RequestBody ExceptionDtos.BulkAssignRequest request) {
        return new ExceptionDtos.BulkAssignResponse(workflow.bulkAssign(request.ids(), request.assignee()));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Append an investigation note")
    public ExceptionDtos.ExceptionDetail comment(
            @PathVariable Long id, @Valid @RequestBody ExceptionDtos.CommentRequest request) {
        workflow.comment(id, request.body());
        return ExceptionDtos.ExceptionDetail.of(workflow.requireWithComments(id));
    }

    /** Maker step: propose a resolution. Cannot be performed by an approver acting alone. */
    @PostMapping("/{id}/submit")
    @PreAuthorize(ReconRoles.HAS_OPERATOR)
    @Operation(summary = "Submit a proposed resolution for approval")
    public ExceptionDtos.ExceptionRow submit(
            @PathVariable Long id, @Valid @RequestBody ExceptionDtos.SubmitRequest request) {
        return ExceptionDtos.ExceptionRow.of(workflow.submitForApproval(id, request.note()));
    }

    /** Checker step. The service refuses self-approval regardless of what this endpoint allows. */
    @PostMapping("/{id}/decision")
    @PreAuthorize(ReconRoles.HAS_APPROVER)
    @Operation(summary = "Approve, reject or write off a submitted resolution")
    public ExceptionDtos.ExceptionRow decide(
            @PathVariable Long id, @Valid @RequestBody ExceptionDtos.DecisionRequest request) {
        return ExceptionDtos.ExceptionRow.of(workflow.decide(id, request.decision(), request.note()));
    }

    /** Whitelist rather than pass-through: an arbitrary sort field is a property-injection hole. */
    private static Sort sortOf(String sortBy, String sortDir) {
        String property = switch (sortBy) {
            case "exposure" -> "exposure.value";
            case "merchant" -> "merchantId";
            case "ref" -> "externalRef";
            case "status" -> "status";
            case "severity" -> "severity";
            case "state" -> "state";
            case "updated" -> "updatedAt";
            default -> "id";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
