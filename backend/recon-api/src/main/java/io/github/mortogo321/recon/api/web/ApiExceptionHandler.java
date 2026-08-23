package io.github.mortogo321.recon.api.web;

import java.net.URI;

import jakarta.persistence.OptimisticLockException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import io.github.mortogo321.recon.api.security.TokenService;
import io.github.mortogo321.recon.batch.service.ReconJobOperations;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.core.service.ExceptionWorkflowService;
import io.github.mortogo321.recon.core.service.ReconRunService;
import io.github.mortogo321.recon.core.service.ToleranceProfileRegistry;
import io.github.mortogo321.recon.domain.money.Money;

/**
 * Every error leaves this API as RFC 7807 {@code application/problem+json} with a stable
 * {@code type} URI, so the console can branch on the type rather than on a status code plus
 * string matching. Domain exceptions map to specific types; anything unmapped is a 500 with the
 * detail withheld, because an unexpected stack trace is not the client's business.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE = "https://recon.example/problems/";

    @ExceptionHandler(ReconRunService.ReconRunNotFoundException.class)
    public ProblemDetail onRunNotFound(ReconRunService.ReconRunNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "run-not-found", "Reconciliation run not found", e.getMessage());
    }

    @ExceptionHandler(ExceptionWorkflowService.ExceptionNotFoundException.class)
    public ProblemDetail onExceptionNotFound(ExceptionWorkflowService.ExceptionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "exception-not-found", "Exception not found", e.getMessage());
    }

    /** The maker-checker control. Distinct type so the console can explain *why* it was refused. */
    @ExceptionHandler(ExceptionWorkflowService.SelfApprovalException.class)
    public ProblemDetail onSelfApproval(ExceptionWorkflowService.SelfApprovalException e) {
        ProblemDetail detail = problem(
                HttpStatus.FORBIDDEN,
                "self-approval-forbidden",
                "Self-approval is not permitted",
                e.getMessage());
        detail.setProperty("exceptionId", e.exceptionId());
        return detail;
    }

    @ExceptionHandler(ReconExceptionEntity.IllegalStateTransitionException.class)
    public ProblemDetail onIllegalTransition(ReconExceptionEntity.IllegalStateTransitionException e) {
        ProblemDetail detail =
                problem(HttpStatus.CONFLICT, "illegal-state-transition", "Illegal workflow transition", e.getMessage());
        detail.setProperty("from", e.from().name());
        detail.setProperty("to", e.to().name());
        return detail;
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail onOptimisticLock(OptimisticLockException e) {
        return problem(
                HttpStatus.CONFLICT,
                "concurrent-modification",
                "Someone else changed this record",
                "Reload the exception and try again.");
    }

    @ExceptionHandler(ToleranceProfileRegistry.UnknownToleranceProfileException.class)
    public ProblemDetail onUnknownProfile(ToleranceProfileRegistry.UnknownToleranceProfileException e) {
        return problem(HttpStatus.BAD_REQUEST, "unknown-tolerance-profile", "Unknown tolerance profile", e.getMessage());
    }

    @ExceptionHandler(Money.CurrencyMismatchException.class)
    public ProblemDetail onCurrencyMismatch(Money.CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "currency-mismatch", "Currency mismatch", e.getMessage());
    }

    @ExceptionHandler(ReconJobOperations.JobOperationException.class)
    public ProblemDetail onJobOperation(ReconJobOperations.JobOperationException e) {
        log.warn("Batch operation '{}' rejected: {}", e.operation(), e.getMessage());
        ProblemDetail detail =
                problem(HttpStatus.CONFLICT, "batch-operation-refused", "Batch operation refused", e.getMessage());
        detail.setProperty("operation", e.operation());
        return detail;
    }

    @ExceptionHandler(TokenService.InvalidCredentialsException.class)
    public ProblemDetail onBadCredentials(TokenService.InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Authentication failed", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException e) {
        return problem(
                HttpStatus.FORBIDDEN, "access-denied", "Insufficient permissions", "Your role does not allow this.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException e) {
        ProblemDetail detail =
                problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed", "See 'errors'.");
        detail.setProperty(
                "errors",
                e.getBindingResult().getFieldErrors().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                org.springframework.validation.FieldError::getField,
                                error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                                (a, b) -> a)));
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception e, WebRequest request) {
        log.error("Unhandled exception for {}", request.getDescription(false), e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Unexpected error",
                "The request could not be completed. Quote the X-Correlation-Id header when reporting this.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
