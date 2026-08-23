package io.github.mortogo321.recon.core.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mortogo321.recon.core.entity.ExceptionState;
import io.github.mortogo321.recon.core.entity.ReconExceptionEntity;
import io.github.mortogo321.recon.domain.match.MatchSeverity;
import io.github.mortogo321.recon.domain.match.MatchStatus;

public interface ReconExceptionRepository
        extends JpaRepository<ReconExceptionEntity, Long>, JpaSpecificationExecutor<ReconExceptionEntity> {

    /** Detail drawer: one query for the break and its whole comment trail, no N+1. */
    @EntityGraph(attributePaths = "comments")
    Optional<ReconExceptionEntity> findWithCommentsById(Long id);

    boolean existsByRunIdAndMerchantIdAndExternalRefAndStatus(
            Long runId, String merchantId, String externalRef, MatchStatus status);

    /**
     * Keyset pagination for the grid. Deep OFFSET on a table with millions of breaks makes the
     * last pages progressively slower; seeking on the descending id is flat.
     */
    @Query(
            """
            select e from ReconExceptionEntity e
             where e.run.id = :runId
               and (:afterId is null or e.id < :afterId)
             order by e.id desc
            """)
    List<ReconExceptionEntity> findPageByRun(
            @Param("runId") Long runId, @Param("afterId") Long afterId, Limit limit);

    /** Bulk assignment from the grid's multi-select. One statement, not one per row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update ReconExceptionEntity e
               set e.assignedTo = :assignee,
                   e.state      = :state,
                   e.updatedAt  = :now,
                   e.updatedBy  = :actor,
                   e.version    = e.version + 1
             where e.id in :ids
               and e.state in :fromStates
            """)
    int bulkAssign(
            @Param("ids") Collection<Long> ids,
            @Param("assignee") String assignee,
            @Param("state") ExceptionState state,
            @Param("fromStates") Collection<ExceptionState> fromStates,
            @Param("actor") String actor,
            @Param("now") Instant now);

    @Query(
            """
            select e.status as status, e.severity as severity, count(e) as total,
                   coalesce(sum(e.exposure.value), 0) as exposure
              from ReconExceptionEntity e
             where e.run.id = :runId
             group by e.status, e.severity
            """)
    List<StatusBreakdownRow> breakdownByRun(@Param("runId") Long runId);

    @Query(
            """
            select e.state as state, count(e) as total
              from ReconExceptionEntity e
             where e.run.id = :runId
             group by e.state
            """)
    List<StateCountRow> stateCountsByRun(@Param("runId") Long runId);

    long countByRunIdAndStateIn(Long runId, Collection<ExceptionState> states);

    interface StatusBreakdownRow {
        MatchStatus getStatus();

        MatchSeverity getSeverity();

        long getTotal();

        java.math.BigDecimal getExposure();
    }

    interface StateCountRow {
        ExceptionState getState();

        long getTotal();
    }
}
