package io.github.mortogo321.recon.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mortogo321.recon.core.config.CurrentActorProvider;
import io.github.mortogo321.recon.core.entity.ExceptionCommentEntity;
import io.github.mortogo321.recon.core.entity.ExceptionState;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.core.event.ReconEvents;
import io.github.mortogo321.recon.core.repository.ReconExceptionRepository;

/**
 * The operator workflow, including the maker-checker gate.
 *
 * <p>The rule that actually matters: the person who proposes a resolution cannot be the person who
 * approves it. It is enforced here rather than in the controller because it is a property of the
 * domain, not of one HTTP endpoint, and because the batch and any future CLI must obey it too.
 */
@Service
public class ExceptionWorkflowService {

    private static final Set<ExceptionState> ASSIGNABLE_FROM =
            Set.of(ExceptionState.OPEN, ExceptionState.INVESTIGATING, ExceptionState.REJECTED);

    private final ReconExceptionRepository exceptions;
    private final OutboxWriter outbox;
    private final CurrentActorProvider actor;
    private final Clock clock;

    public ExceptionWorkflowService(
            ReconExceptionRepository exceptions, OutboxWriter outbox, CurrentActorProvider actor, Clock clock) {
        this.exceptions = exceptions;
        this.outbox = outbox;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ReconExceptionEntity> search(Specification<ReconExceptionEntity> spec, Pageable pageable) {
        return exceptions.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<ReconExceptionEntity> pageByRun(Long runId, Long afterId, int limit) {
        return exceptions.findPageByRun(runId, afterId, Limit.of(Math.clamp(limit, 1, 500)));
    }

    @Transactional(readOnly = true)
    public ReconExceptionEntity requireWithComments(Long id) {
        return exceptions.findWithCommentsById(id).orElseThrow(() -> new ExceptionNotFoundException(id));
    }

    @Transactional
    public ReconExceptionEntity assign(Long id, String assignee) {
        ReconExceptionEntity exception = require(id);
        exception.assignTo(assignee);
        return exception;
    }

    @Transactional
    public int bulkAssign(Collection<Long> ids, String assignee) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return exceptions.bulkAssign(
                ids,
                assignee,
                ExceptionState.INVESTIGATING,
                ASSIGNABLE_FROM,
                actor.currentActor(),
                Instant.now(clock));
    }

    @Transactional
    public ReconExceptionEntity comment(Long id, String body) {
        ReconExceptionEntity exception = require(id);
        exception.addComment(new ExceptionCommentEntity(actor.currentActor(), body));
        return exception;
    }

    /** Maker step. Records who proposed what, and leaves the break waiting for a second pair of eyes. */
    @Transactional
    public ReconExceptionEntity submitForApproval(Long id, String note) {
        ReconExceptionEntity exception = require(id);
        exception.submitForApproval(actor.currentActor(), note, Instant.now(clock));
        return exception;
    }

    /**
     * Checker step. Rejects self-approval outright — a break that one person both raised and signed
     * off is exactly the control failure this workflow exists to prevent.
     */
    @Transactional
    public ReconExceptionEntity decide(Long id, ExceptionState decision, String note) {
        ReconExceptionEntity exception = require(id);
        String approver = actor.currentActor();
        if (approver != null && approver.equals(exception.getSubmittedBy())) {
            throw new SelfApprovalException(id, approver);
        }
        Instant now = Instant.now(clock);
        if (note != null && !note.isBlank()) {
            exception.addComment(new ExceptionCommentEntity(approver, note));
        }
        exception.decide(decision, approver, now);
        outbox.record(
                "ReconException",
                new ReconEvents.ExceptionDecided(
                        exception.getId(),
                        exception.getRun().getId(),
                        exception.getMerchantId(),
                        exception.getExternalRef(),
                        decision.name(),
                        approver,
                        now));
        return exception;
    }

    @Transactional(readOnly = true)
    public List<ReconExceptionRepository.StatusBreakdownRow> breakdown(Long runId) {
        return exceptions.breakdownByRun(runId);
    }

    @Transactional(readOnly = true)
    public List<ReconExceptionRepository.StateCountRow> stateCounts(Long runId) {
        return exceptions.stateCountsByRun(runId);
    }

    @Transactional(readOnly = true)
    public long openCount(Long runId) {
        return exceptions.countByRunIdAndStateIn(
                runId, List.of(ExceptionState.OPEN, ExceptionState.INVESTIGATING, ExceptionState.PENDING_APPROVAL));
    }

    private ReconExceptionEntity require(Long id) {
        return exceptions.findById(id).orElseThrow(() -> new ExceptionNotFoundException(id));
    }

    public static final class ExceptionNotFoundException extends RuntimeException {
        private final Long id;

        public ExceptionNotFoundException(Long id) {
            super("Reconciliation exception " + id + " does not exist");
            this.id = id;
        }

        public Long id() {
            return id;
        }
    }

    /** Maker-checker violation: distinct from a generic 403 so it can be audited separately. */
    public static final class SelfApprovalException extends RuntimeException {
        private final Long exceptionId;
        private final String user;

        public SelfApprovalException(Long exceptionId, String user) {
            super("User " + user + " submitted exception " + exceptionId + " and may not approve it");
            this.exceptionId = exceptionId;
            this.user = user;
        }

        public Long exceptionId() {
            return exceptionId;
        }

        public String user() {
            return user;
        }
    }
}
