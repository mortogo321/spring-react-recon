package io.github.mortogo321.recon.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.mortogo321.recon.api.dto.CommonDtos.MoneyDto;
import io.github.mortogo321.recon.core.entity.ExceptionState;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;

public final class ExceptionDtos {

    private ExceptionDtos() {}

    /** Grid row: everything the DataGrid renders, and nothing that would need a second query. */
    public record ExceptionRow(
            Long id,
            Long runId,
            String merchantId,
            String externalRef,
            MatchStatus status,
            MatchSeverity severity,
            ExceptionState state,
            MoneyDto settlementAmount,
            MoneyDto ledgerAmount,
            MoneyDto exposure,
            String detail,
            String assignedTo,
            String submittedBy,
            Instant updatedAt,
            long version,
            Set<ExceptionState> allowedTransitions) {

        public static ExceptionRow of(ReconExceptionEntity entity) {
            return new ExceptionRow(
                    entity.getId(),
                    entity.getRun().getId(),
                    entity.getMerchantId(),
                    entity.getExternalRef(),
                    entity.getStatus(),
                    entity.getSeverity(),
                    entity.getState(),
                    MoneyDto.of(entity.getSettlementAmount()),
                    MoneyDto.of(entity.getLedgerAmount()),
                    MoneyDto.of(entity.getExposure()),
                    entity.getDetail(),
                    entity.getAssignedTo(),
                    entity.getSubmittedBy(),
                    entity.getUpdatedAt(),
                    entity.getVersion(),
                    entity.getState().allowedNext());
        }
    }

    public record CommentView(Long id, String author, String body, Instant createdAt) {}

    /** Detail drawer: the row plus its full audit trail. */
    public record ExceptionDetail(
            ExceptionRow exception,
            String resolutionNote,
            Instant submittedAt,
            String decidedBy,
            Instant decidedAt,
            List<CommentView> comments) {

        public static ExceptionDetail of(ReconExceptionEntity entity) {
            return new ExceptionDetail(
                    ExceptionRow.of(entity),
                    entity.getResolutionNote(),
                    entity.getSubmittedAt(),
                    entity.getDecidedBy(),
                    entity.getDecidedAt(),
                    entity.getComments().stream()
                            .map(c -> new CommentView(c.getId(), c.getAuthor(), c.getBody(), c.getCreatedAt()))
                            .toList());
        }
    }

    public record AssignRequest(@NotBlank @Size(max = 64) String assignee) {}

    public record BulkAssignRequest(
            @NotEmpty @Size(max = 500) List<Long> ids, @NotBlank @Size(max = 64) String assignee) {}

    public record CommentRequest(@NotBlank @Size(max = 2000) String body) {}

    public record SubmitRequest(@NotBlank @Size(max = 1024) String note) {}

    /**
     * Only three of the six states are reachable by a decision, and the enum is validated rather
     * than trusted — a client that posts RESOLVED straight from OPEN must be refused.
     */
    public record DecisionRequest(@NotNull ExceptionState decision, @Size(max = 1024) String note) {}

    public record BulkAssignResponse(int updated) {}
}
