package com.zillya.timonfech.zillwrapper.core.pending;

import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskStatus;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskType;
import com.zillya.timonfech.zillwrapper.core.repos.PendingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingTaskService {

    private final PendingTaskRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${pending.task.ttl.order-preview-minutes:15}")
    private long orderPreviewTtlMinutes;

    @Transactional
    public PendingTaskEntity create(PendingTaskType type,
                                    Long sourceId,
                                    Long initiatorUserId,
                                    String sourceActorId,
                                    PendingTaskPayload payload) {
        return create(type, sourceId, initiatorUserId, sourceActorId, payload, resolveExpiresAt(type));
    }

    @Transactional
    public PendingTaskEntity create(PendingTaskType type,
                                    Long sourceId,
                                    Long initiatorUserId,
                                    String sourceActorId,
                                    PendingTaskPayload payload,
                                    Instant expiresAt) {
        PendingTaskEntity entity = new PendingTaskEntity();
        entity.setTaskId(UUID.randomUUID().toString());
        entity.setTaskType(type);
        entity.setStatus(PendingTaskStatus.WAITING);
        entity.setSourceId(sourceId);
        entity.setInitiatorUserId(initiatorUserId);
        entity.setSourceActorId(sourceActorId);
        entity.setPayloadType(payload.getClass().getName());
        entity.setPayloadJson(writePayload(payload));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setExpiresAt(expiresAt);
        return repository.save(entity);
    }

    private Instant resolveExpiresAt(PendingTaskType type) {
        Instant now = Instant.now();
        if (type == PendingTaskType.ORDER_PREVIEW_CONFIRMATION) {
            long minutes = Math.max(1, orderPreviewTtlMinutes);
            return now.plus(Duration.ofMinutes(minutes));
        }
        return now.plus(Duration.ofMinutes(15));
    }

    public Optional<PendingTaskEntity> get(String taskId) {
        return repository.findById(taskId);
    }

    @Transactional
    public PendingTaskEntity markConfirmed(PendingTaskEntity entity) {
        entity.setStatus(PendingTaskStatus.CONFIRMED);
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    @Transactional
    public void markCompleted(PendingTaskEntity entity) {
        entity.setStatus(PendingTaskStatus.COMPLETED);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void markCancelled(String taskId) {
        repository.findById(taskId).ifPresent(entity -> {
            entity.setStatus(PendingTaskStatus.CANCELLED);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    @Transactional
    public void markFailed(PendingTaskEntity entity, String errorMessage) {
        entity.setStatus(PendingTaskStatus.FAILED);
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void markExpired(PendingTaskEntity entity, String errorMessage) {
        entity.setStatus(PendingTaskStatus.EXPIRED);
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    public <T extends PendingTaskPayload> T readPayload(PendingTaskEntity entity, Class<T> payloadType) {
        try {
            return objectMapper.readValue(entity.getPayloadJson(), payloadType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read pending task payload " + entity.getTaskId(), e);
        }
    }

    private String writePayload(PendingTaskPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize pending task payload", e);
        }
    }
}
