package io.github.mortogo321.recon.core.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mortogo321.recon.core.entity.ReconRunEntity;
import io.github.mortogo321.recon.core.entity.RunStatus;

public interface ReconRunRepository extends JpaRepository<ReconRunEntity, Long> {

    Optional<ReconRunEntity> findByRunKey(String runKey);

    /**
     * Pessimistic read for the launch path. Two concurrent triggers for the same business date is
     * an ordinary race (a cron and an impatient operator), and optimistic locking would surface it
     * only after both had already created a run.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReconRunEntity r where r.runKey = :runKey")
    Optional<ReconRunEntity> findByRunKeyForUpdate(@Param("runKey") String runKey);

    List<ReconRunEntity> findByStatusIn(List<RunStatus> statuses);

    List<ReconRunEntity> findByBusinessDateOrderByIdDesc(LocalDate businessDate, Limit limit);

    List<ReconRunEntity> findAllByOrderByBusinessDateDescIdDesc(Limit limit);

    boolean existsByBusinessDateAndStatusIn(LocalDate businessDate, List<RunStatus> statuses);

    /**
     * Dashboard trend series. A projection rather than entities: this powers a sparkline and
     * loading full aggregates with their audit columns for it would be pure waste.
     */
    @Query(
            """
            select r.businessDate      as businessDate,
                   r.matchRate         as matchRate,
                   r.exceptionKeys     as exceptionKeys,
                   r.settlementRows    as settlementRows
              from ReconRunEntity r
             where r.businessDate >= :from
               and r.status in :statuses
             order by r.businessDate asc
            """)
    List<RunTrendRow> findTrend(
            @Param("from") LocalDate from, @Param("statuses") List<RunStatus> statuses);

    /** Closed interface projection — Spring Data generates the proxy, no mapping code needed. */
    interface RunTrendRow {
        LocalDate getBusinessDate();

        java.math.BigDecimal getMatchRate();

        long getExceptionKeys();

        long getSettlementRows();
    }
}
