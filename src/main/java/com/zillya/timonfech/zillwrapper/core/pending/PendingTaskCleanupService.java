package com.zillya.timonfech.zillwrapper.core.pending;

import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskStatus;
import com.zillya.timonfech.zillwrapper.core.repos.PendingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pending.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PendingTaskCleanupService {

    private static final EnumSet<PendingTaskStatus> TERMINAL_STATUSES = EnumSet.of(
            PendingTaskStatus.COMPLETED,
            PendingTaskStatus.CANCELLED,
            PendingTaskStatus.FAILED,
            PendingTaskStatus.EXPIRED
    );

    private final PendingTaskRepository repository;

    @Value("${pending.cleanup.retention-days:7}")
    private long retentionDays;

    @Scheduled(fixedDelayString = "${pending.cleanup.fixed-delay-ms:3600000}")
    @Transactional
    public void cleanup() {
        expireWaitingTasks();
        deleteOldTerminalTasks();
    }

    private void expireWaitingTasks() {
        List<PendingTaskEntity> expired = repository.findByStatusAndExpiresAtBefore(
                PendingTaskStatus.WAITING,
                Instant.now()
        );
        for (PendingTaskEntity task : expired) {
            task.setStatus(PendingTaskStatus.EXPIRED);
            task.setErrorMessage("Pending task expired");
            task.setUpdatedAt(Instant.now());
        }
        if (!expired.isEmpty()) {
            repository.saveAll(expired);
            log.info("Expired {} pending task(s)", expired.size());
        }
    }

    private void deleteOldTerminalTasks() {
        Instant updatedBefore = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        int deleted = repository.deleteByStatusInAndUpdatedAtBefore(TERMINAL_STATUSES, updatedBefore);
        if (deleted > 0) {
            log.info("Deleted {} old terminal pending task(s)", deleted);
        }
    }
}
