package io.github.mortogo321.recon.core.repository;

import java.math.BigDecimal;
import java.util.Collection;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import io.github.mortogo321.recon.core.entity.ExceptionState;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;

/**
 * Composable filters behind the exception grid. Built as Specifications rather than a hand-rolled
 * query string so the console's arbitrary filter combinations stay type-safe and injection-proof.
 */
public final class ReconExceptionSpecifications {

    private ReconExceptionSpecifications() {}

    public static Specification<ReconExceptionEntity> inRun(Long runId) {
        return (root, query, cb) -> runId == null ? cb.conjunction() : cb.equal(root.get("run").get("id"), runId);
    }

    public static Specification<ReconExceptionEntity> hasStatus(Collection<MatchStatus> statuses) {
        return (root, query, cb) ->
                statuses == null || statuses.isEmpty() ? cb.conjunction() : root.get("status").in(statuses);
    }

    public static Specification<ReconExceptionEntity> hasSeverity(Collection<MatchSeverity> severities) {
        return (root, query, cb) ->
                severities == null || severities.isEmpty() ? cb.conjunction() : root.get("severity").in(severities);
    }

    public static Specification<ReconExceptionEntity> hasState(Collection<ExceptionState> states) {
        return (root, query, cb) ->
                states == null || states.isEmpty() ? cb.conjunction() : root.get("state").in(states);
    }

    public static Specification<ReconExceptionEntity> merchant(String merchantId) {
        return (root, query, cb) -> merchantId == null || merchantId.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<ReconExceptionEntity> assignedTo(String user) {
        return (root, query, cb) ->
                user == null || user.isBlank() ? cb.conjunction() : cb.equal(root.get("assignedTo"), user);
    }

    public static Specification<ReconExceptionEntity> exposureAtLeast(BigDecimal minimum) {
        return (root, query, cb) -> minimum == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("exposure").get("value"), minimum);
    }

    /** Free-text search across the two fields an operator actually types into. */
    public static Specification<ReconExceptionEntity> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + term.trim().toLowerCase() + "%";
            Predicate onRef = cb.like(cb.lower(root.get("externalRef")), pattern);
            Predicate onMerchant = cb.like(cb.lower(root.get("merchantId")), pattern);
            Predicate onDetail = cb.like(cb.lower(root.get("detail")), pattern);
            return cb.or(onRef, onMerchant, onDetail);
        };
    }
}
