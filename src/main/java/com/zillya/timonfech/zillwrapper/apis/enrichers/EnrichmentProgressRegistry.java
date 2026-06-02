package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EnrichmentProgressRegistry {

    public record ProgressSnapshot(
            UUID taskId,
            BigInteger operationId,
            long from,
            long to,
            boolean single,
            long processed,
            long total,
            long licensesProcessed,
            long activationsRequired,
            long activationsProcessed,
            long activationsFailed,
            long activationsSkipped,
            Long currentExternalId,
            Instant startedAt,
            Instant updatedAt
    ) {}

    private static final class ProgressState {
        UUID taskId;
        BigInteger operationId;
        long from;
        long to;
        boolean single;
        long processed;
        long total;
        long licensesProcessed;
        long activationsRequired;
        long activationsProcessed;
        long activationsFailed;
        long activationsSkipped;
        Long currentExternalId;
        Instant startedAt;
        Instant updatedAt;
    }

    private final Map<UUID, ProgressState> byTask = new ConcurrentHashMap<>();
    private final Map<BigInteger, UUID> taskByOperation = new ConcurrentHashMap<>();
    private final Map<BigInteger, Long> lastSignalAtMs = new ConcurrentHashMap<>();
    private final OperationExecutionService operationExecutionService;
    private final ApplicationEventPublisher eventPublisher;

    public void start(UUID taskId, BigInteger operationId, long from, long to, boolean single) {
        ProgressState state = new ProgressState();
        state.taskId = taskId;
        state.operationId = operationId;
        state.from = from;
        state.to = to;
        state.single = single;
        state.total = estimateTotal(from, to, single);
        state.startedAt = Instant.now();
        state.updatedAt = state.startedAt;
        byTask.put(taskId, state);
        if (operationId != null) {
            taskByOperation.put(operationId, taskId);
        }
    }

    public void tick(UUID taskId, long externalId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.processed++;
        state.currentExternalId = externalId;
        state.updatedAt = Instant.now();
        signalProgress(state);
    }

    public void markLicenseProcessed(UUID taskId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.licensesProcessed++;
        state.updatedAt = Instant.now();
    }

    public void markActivationsRequired(UUID taskId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.activationsRequired++;
        state.updatedAt = Instant.now();
    }

    public void markActivationsProcessed(UUID taskId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.activationsProcessed++;
        state.updatedAt = Instant.now();
    }

    public void markActivationsFailed(UUID taskId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.activationsFailed++;
        state.updatedAt = Instant.now();
    }

    public void markActivationsSkipped(UUID taskId) {
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return;
        }
        state.activationsSkipped++;
        state.updatedAt = Instant.now();
    }

    public Optional<ProgressSnapshot> findByOperationId(BigInteger operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        UUID taskId = taskByOperation.get(operationId);
        if (taskId == null) {
            return Optional.empty();
        }
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(state));
    }

    public Optional<ProgressSnapshot> findByTaskId(UUID taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        ProgressState state = byTask.get(taskId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(state));
    }

    public void finish(UUID taskId) {
        ProgressState removed = byTask.remove(taskId);
        if (removed != null && removed.operationId != null) {
            taskByOperation.remove(removed.operationId);
            lastSignalAtMs.remove(removed.operationId);
        }
    }

    private ProgressSnapshot toSnapshot(ProgressState state) {
        return new ProgressSnapshot(
                state.taskId,
                state.operationId,
                state.from,
                state.to,
                state.single,
                state.processed,
                state.total,
                state.licensesProcessed,
                state.activationsRequired,
                state.activationsProcessed,
                state.activationsFailed,
                state.activationsSkipped,
                state.currentExternalId,
                state.startedAt,
                state.updatedAt
        );
    }

    private long estimateTotal(long from, long to, boolean single) {
        if (single) {
            return 1;
        }
        long max = Math.max(from, to);
        long min = Math.min(from, to);
        return (max - min) + 1;
    }

    private void signalProgress(ProgressState state) {
        if (state.operationId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSignalAtMs.get(state.operationId);
        if (last != null && now - last < 350L) {
            return;
        }
        lastSignalAtMs.put(state.operationId, now);
        operationExecutionService.getOperation(state.operationId).ifPresent(root ->
                eventPublisher.publishEvent(new HandlingInfoEvent(this, root)));
    }
}
