package io.github.mortogo321.recon.core.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mortogo321.recon.core.entity.OutboxEventEntity;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claim a batch of pending events. {@code SKIP LOCKED} is what makes it safe to run more than
     * one dispatcher instance: each picks a disjoint set instead of blocking on the other.
     */
    @Query(
            value =
                    """
                    select * from outbox_event
                     where status = 'PENDING'
                     order by occurred_at asc
                     limit :max
                     for update skip locked
                    """,
            nativeQuery = true)
    List<OutboxEventEntity> claimPending(@Param("max") int max);

    List<OutboxEventEntity> findByStatusOrderByOccurredAtAsc(OutboxEventEntity.Status status, Limit limit);

    long countByStatus(OutboxEventEntity.Status status);

    @Modifying
    @Query("delete from OutboxEventEntity e where e.status = 'PUBLISHED' and e.publishedAt < :before")
    int purgePublishedBefore(@Param("before") Instant before);
}
