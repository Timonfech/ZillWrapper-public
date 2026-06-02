package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PendingTaskRepository extends JpaRepository<PendingTaskEntity, String> {
    List<PendingTaskEntity> findByStatusAndExpiresAtBefore(PendingTaskStatus status, Instant expiresAt);

    @Modifying
    @Query("delete from PendingTaskEntity p where p.status in :statuses and p.updatedAt < :updatedBefore")
    int deleteByStatusInAndUpdatedAtBefore(@Param("statuses") Collection<PendingTaskStatus> statuses,
                                           @Param("updatedBefore") Instant updatedBefore);
}
