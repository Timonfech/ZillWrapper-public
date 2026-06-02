package com.zillya.timonfech.zillwrapper.core.services;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionKind;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.PlanStep;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.events.OperationCompletedEvent;
import com.zillya.timonfech.zillwrapper.core.events.OperationCreatedEvent;
import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.repos.OperationExecutionRepository;
import com.zillya.timonfech.zillwrapper.core.security.OperationInteractionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationExecutionService {
    private static final int OPERATION_ERROR_MESSAGE_MAX_LEN = 255;

    private final OperationExecutionRepository repository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final OperationInteractionPolicy interactionPolicy;
    private final ObjectMapper objectMapper;

    /**
     * Gets a fresh copy of the operation directly from DB, bypassing internal cache and L1 cache.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Optional<OperationExecutionEntity> getFreshOperation(BigInteger id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }

    /**
     * Gets an operation by ID.
     */
    public Optional<OperationExecutionEntity> getOperation(BigInteger id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }

    /**
     * Saves or updates an operation.
     */
    @Transactional
    public OperationExecutionEntity save(OperationExecutionEntity entity) {
        return repository.save(entity);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public OperationExecutionEntity createOperation(OperationExecutionEntity entity,
                                                    InboundEvent<?> sourceContext) {
        if (entity.getExecutionKind() == OperationExecutionKind.PARENT) {
            entity.setInteractionEnabled(interactionPolicy.isInteractive(
                    sourceContext,
                    entity.getSourceId(),
                    entity.getOperationType()
            ));
        }
        OperationExecutionEntity saved = repository.save(entity);
        if (sourceContext != null && saved.getExecutionKind() == OperationExecutionKind.PARENT) {
            eventPublisher.publishEvent(new OperationCreatedEvent(this, saved.getId(), sourceContext));
        }
        return saved;
    }

    @Transactional
    public OperationExecutionEntity createParentOperation(IOperationContext context, OperationType rootType) {
        OperationExecutionEntity parent = new OperationExecutionEntity();
        parent.setExecutionKind(OperationExecutionKind.PARENT);
        parent.setSourceId(context.getEntitySourceId());
        parent.setOperationType(rootType);
        parent.setInitiatorUserId(context.getInitiatorUserId());
        parent.setStatus(OperationStatus.RUNNING);
        parent.setAttempt(1);
        parent.setRecoverable(false);
        parent.setCancelable(true);

        OperationExecutionEntity saved = createOperation(parent, context.getSourceContext());
        context.setOperationId(saved.getId());
        return saved;
    }

    @Transactional
    public void ensurePlannedStages(BigInteger parentOperationId, OrderOperationContext context, ExecutionPlan plan) {
        if (parentOperationId == null || context == null || plan == null) {
            return;
        }
        List<OperationExecutionEntity> existing = getChildren(parentOperationId);
        if (plan.steps().isEmpty()) {
            throw new IllegalStateException("Pipeline plan is empty for parent operation " + parentOperationId);
        }
        OperationExecutionEntity parent = getRootOperation(parentOperationId).orElse(null);
        if (parent == null) {
            throw new IllegalStateException("Parent operation not found for stage planning: " + parentOperationId);
        }
        parent.setExecutionPlanJson(serializeExecutionPlan(plan));
        repository.save(parent);

        if (!existing.isEmpty()) {
            boolean allPending = existing.stream().allMatch(s -> s.getStatus() == OperationStatus.PENDING);
            if (!allPending) {
                log.warn("Skip plan reconciliation for parentOpId={} because not all stages are pending", parentOperationId);
                return;
            }
            java.util.Set<OperationType> existingTypes = existing.stream()
                    .map(OperationExecutionEntity::getOperationType)
                    .collect(java.util.stream.Collectors.toSet());
            for (PlanStep step : plan.steps()) {
                if (existingTypes.contains(step.stageType())) {
                    continue;
                }
                repository.save(buildStageEntity(parentOperationId, context, parent, step));
            }
            return;
        }

        for (PlanStep step : plan.steps()) {
            repository.save(buildStageEntity(parentOperationId, context, parent, step));
        }
    }

    private OperationExecutionEntity buildStageEntity(BigInteger parentOperationId,
                                                      OrderOperationContext context,
                                                      OperationExecutionEntity parent,
                                                      PlanStep step) {
        OperationExecutionEntity stage = new OperationExecutionEntity();
        stage.setParentId(parentOperationId);
        stage.setExecutionKind(OperationExecutionKind.STAGE);
        stage.setSourceId(context.getEntitySourceId());
        stage.setOperationType(step.stageType());
        stage.setInitiatorUserId(context.getInitiatorUserId());
        stage.setStatus(OperationStatus.PENDING);
        stage.setAttempt(0);
        stage.setRecoverable(false);
        stage.setCancelable(step.cancelable() != null ? step.cancelable() : !isCrucial(step.stageType()));
        stage.setNonBlocking(step.nonBlocking() != null && step.nonBlocking());
        stage.setSequenceNo(step.sequenceNo());
        stage.setInteractionEnabled(parent.isInteractionEnabled());
        if (context.getOrderId() != null) {
            stage.setEntityId(context.getOrderId());
            stage.setEntityTypeEnum(com.zillya.timonfech.zillwrapper.EntityTypeEnum.ORDER);
        }
        return stage;
    }

    @Transactional
    public void markParentFailed(BigInteger operationId, String errorMessage) {
        resolveRootOperation(operationId).ifPresent(root -> {
            root.setStatus(OperationStatus.FAILED);
            root.setErrorMessage(truncateErrorMessage(errorMessage));
            repository.save(root);
        });
    }

    @Transactional
    public void markParentDone(BigInteger operationId) {
        markParentDone(operationId, null);
    }

    @Transactional
    public void markParentDone(BigInteger operationId, String summaryMessage) {
        resolveRootOperation(operationId).ifPresent(root -> {
            root.setStatus(OperationStatus.DONE);
            root.setErrorMessage(truncateErrorMessage(summaryMessage));
            root.setCompletedAt(Instant.now());
            repository.save(root);
            publishHandlingInfoIfInteractive(root);
            eventPublisher.publishEvent(new OperationCompletedEvent(this, root));
        });
    }

    @Transactional
    public void markStageCompleted(BigInteger stageExecutionId, OperationStatus status, String summaryMessage) {
        getOperation(stageExecutionId).ifPresent(stage -> {
            stage.setStatus(status);
            if (status == OperationStatus.FAILED || status == OperationStatus.PARTIALLY_DONE) {
                stage.setErrorMessage(truncateErrorMessage(summaryMessage));
            } else {
                stage.setErrorMessage(null);
            }
            stage.setCompletedAt(Instant.now());
            repository.save(stage);
            publishHandlingInfoIfInteractive(stage);
        });
    }

    @Transactional
    public void markParentRunning(BigInteger operationId) {
        resolveRootOperation(operationId).ifPresent(root -> {
            root.setStatus(OperationStatus.RUNNING);
            repository.save(root);
        });
    }

    @Transactional
    public void markParentWaiting(BigInteger operationId) {
        resolveRootOperation(operationId).ifPresent(root -> {
            root.setStatus(OperationStatus.WAITING_INTERACTION);
            repository.save(root);
        });
    }

    public List<OperationExecutionEntity> getChildren(BigInteger parentOperationId) {
        return repository.findOrderedChildren(parentOperationId);
    }

    public boolean hasCrucialWaitingChildren(BigInteger parentOperationId) {
        return repository.findByParentId(parentOperationId).stream()
                .anyMatch(child -> child.getStatus() == OperationStatus.WAITING_INTERACTION && !child.isCancelable());
    }

    @Transactional
    public void unblockParentIfNoCrucialWaiting(BigInteger operationId) {
        resolveRootOperation(operationId).ifPresent(root -> {
            if (root.getStatus() != OperationStatus.WAITING_INTERACTION) {
                return;
            }
            boolean hasWaitingCrucial = hasCrucialWaitingChildren(root.getId());
            if (!hasWaitingCrucial) {
                root.setStatus(OperationStatus.RUNNING);
                repository.save(root);
            }
        });
    }

    public Optional<OperationExecutionEntity> getRootOperation(BigInteger operationId) {
        return resolveRootOperation(operationId);
    }

    /**
     * Convenience method to get status without exposing the whole entity.
     */
    public OperationStatus getStatus(BigInteger id) {
        return getOperation(id)
                .map(OperationExecutionEntity::getStatus)
                .orElse(OperationStatus.FAILED);
    }

    @Transactional
    public void cancel(BigInteger id) {
        resolveRootOperation(id).ifPresent(root -> {
            if (!root.isCancelable()) {
                log.info("Operation {} is not cancelable. Ignore cancel request.", root.getId());
                return;
            }

            List<OperationExecutionEntity> children = repository.findByParentId(root.getId());
            boolean hasActiveNonCancelableStage = children.stream()
                    .anyMatch(child -> !child.isCancelable() && isActive(child.getStatus()));
            if (hasActiveNonCancelableStage) {
                log.info("Operation {} has active non-cancelable stage. Ignore cancel request.", root.getId());
                return;
            }

            root.setStatus(OperationStatus.CANCELLED);
            repository.save(root);

            for (OperationExecutionEntity child : children) {
                if (!isTerminal(child.getStatus())) {
                    child.setStatus(OperationStatus.CANCELLED);
                    repository.save(child);
                }
            }
            publishHandlingInfoIfInteractive(root);

            log.info("Operation {} and cancelable children were cancelled by user", root.getId());
        });
    }

    @Transactional
    public void pause(BigInteger id) {
        resolveRootOperation(id).ifPresent(root -> {
            root.setStatus(OperationStatus.PAUSE);
            repository.save(root);

            List<OperationExecutionEntity> children = repository.findByParentId(root.getId());
            for (OperationExecutionEntity child : children) {
                if (child.getStatus() == OperationStatus.RUNNING) {
                    child.setStatus(OperationStatus.PAUSE);
                    repository.save(child);
                }
            }
            publishHandlingInfoIfInteractive(root);

            log.info("Operation {} paused by user", root.getId());
        });
    }

    @Transactional
    public void resume(BigInteger id) {
        resolveRootOperation(id).ifPresent(root -> {
            root.setStatus(OperationStatus.RUNNING);
            repository.save(root);

            List<OperationExecutionEntity> children = repository.findByParentId(root.getId());
            for (OperationExecutionEntity child : children) {
                if (child.getStatus() == OperationStatus.PAUSE) {
                    child.setStatus(OperationStatus.RUNNING);
                    repository.save(child);
                }
            }
            publishHandlingInfoIfInteractive(root);

            log.info("Operation {} resumed by user", root.getId());
        });
    }

    private Optional<OperationExecutionEntity> resolveRootOperation(BigInteger id) {
        return getOperation(id).flatMap(op -> {
            if (op.getParentId() != null) {
                return getOperation(op.getParentId());
            }
            return Optional.of(op);
        });
    }

    private boolean isTerminal(OperationStatus status) {
        return status == OperationStatus.DONE
                || status == OperationStatus.PARTIALLY_DONE
                || status == OperationStatus.FAILED
                || status == OperationStatus.CANCELLED;
    }

    private boolean isActive(OperationStatus status) {
        return status == OperationStatus.RUNNING
                || status == OperationStatus.PAUSE
                || status == OperationStatus.RESUME
                || status == OperationStatus.WAITING_INTERACTION;
    }

    private void publishHandlingInfoIfInteractive(OperationExecutionEntity execution) {
        if (execution != null && execution.isInteractionEnabled()) {
            eventPublisher.publishEvent(new HandlingInfoEvent(this, execution));
        }
    }

    private String truncateErrorMessage(String message) {
        if (message == null || message.length() <= OPERATION_ERROR_MESSAGE_MAX_LEN) {
            return message;
        }
        String suffix = "...";
        int max = OPERATION_ERROR_MESSAGE_MAX_LEN - suffix.length();
        if (max <= 0) {
            return suffix;
        }
        return message.substring(0, max) + suffix;
    }

    private boolean isCrucial(OperationType stageType) {
        return stageType == OperationType.ORDER_CREATION
                || stageType == OperationType.LICENSE_GENERATION
                || stageType == OperationType.ARTIFACT_GENERATION;
    }

    private String serializeExecutionPlan(ExecutionPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize execution plan", e);
        }
    }
}
