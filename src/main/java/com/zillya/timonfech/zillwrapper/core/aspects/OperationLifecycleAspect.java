package com.zillya.timonfech.zillwrapper.core.aspects;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.events.QuestionRequiredEvent;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLifecycleAspect {
    private static final int OPERATION_ERROR_MESSAGE_MAX_LEN = 255;

    private final OperationExecutionService operationService;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Around("@annotation(step) && args(context,..)")
    public Object manageLifecycle(ProceedingJoinPoint joinPoint, OperationStep step, IOperationContext context) throws Throwable {
        OperationExecutionEntity parentExecution = getOrCreateParentExecution(context, step);
        OperationExecutionEntity stageExecution = getOrCreateStageExecution(context, parentExecution, step);

        parentExecution = operationService.getFreshOperation(parentExecution.getId()).orElse(parentExecution);
        stageExecution = operationService.getFreshOperation(stageExecution.getId()).orElse(stageExecution);

        // Parent is authoritative for lifecycle controls.
        if (parentExecution.getStatus() == OperationStatus.PAUSE) {
            log.info("Parent operation {} is paused. Skip stage {}", parentExecution.getId(), step.type());
            return null;
        }
        if (parentExecution.getStatus() == OperationStatus.WAITING_INTERACTION
                && !isResumeOfWaitingStage(context, stageExecution)) {
            log.info("Parent operation {} is waiting for interaction. Skip stage {}", parentExecution.getId(), step.type());
            return null;
        }
        if (parentExecution.getStatus() == OperationStatus.CANCELLED) {
            log.info("Parent operation {} was cancelled. Abort stage {}", parentExecution.getId(), step.type());
            throw new OperationCancelledException("Operation was cancelled by user");
        }

        stageExecution.setHandlerName(((OperationHandler<?>) joinPoint.getTarget()).name());
        stageExecution.setStatus(OperationStatus.RUNNING);
        stageExecution = operationService.save(stageExecution);
        context.setStageExecutionId(stageExecution.getId());
        publishHandlingInfoIfInteractive(stageExecution);

        List<OperationStep.Props> stepPropsList = Arrays.asList(step.stepProps());

        try {
            Object result = joinPoint.proceed();

            Long stageEntityId = resolveStageEntityId(context);
            if (stageEntityId != null) {
                stageExecution.setEntityId(stageEntityId);
            }
            EntityTypeEnum stageEntityType = resolveEntityType(context);
            if (stageEntityType != null) {
                stageExecution.setEntityTypeEnum(stageEntityType);
            }

            if (result instanceof OperationResult<?> operationResult && !operationResult.isSuccess()) {
                stageExecution.setStatus(OperationStatus.FAILED);
                stageExecution.setRecoverable(operationResult.isRecoverable());
                stageExecution.setErrorMessage(truncateForErrorMessage(operationResult.getErrorMessage()));
            } else if (stepPropsList.contains(OperationStep.Props.ASYNC)) {
                // Async stages are finalized by their dedicated listeners/workers.
                // Re-read to avoid overwriting a terminal status set by async callback race.
                OperationExecutionEntity freshStage = operationService.getOperation(stageExecution.getId()).orElse(stageExecution);
                OperationStatus freshStatus = freshStage.getStatus();
                if (freshStatus == OperationStatus.DONE
                        || freshStatus == OperationStatus.PARTIALLY_DONE
                        || freshStatus == OperationStatus.FAILED
                        || freshStatus == OperationStatus.CANCELLED) {
                    stageExecution = freshStage;
                } else {
                    stageExecution.setStatus(OperationStatus.RUNNING);
                }
            } else {
                stageExecution.setStatus(OperationStatus.DONE);
                if (result instanceof OperationResult<?> operationResult
                        && operationResult.getPayload() instanceof String payload
                        && payload != null
                        && !payload.isBlank()) {
                    stageExecution.setErrorMessage(truncateForErrorMessage(payload));
                }
                stageExecution.setCompletedAt(Instant.now());
            }

            operationService.save(stageExecution);
            if (stepPropsList.contains(OperationStep.Props.FINAL)
                    && stageExecution.getStatus() == OperationStatus.DONE) {
                operationService.markParentDone(parentExecution.getId());
                parentExecution = operationService.getOperation(parentExecution.getId()).orElse(parentExecution);
            }
            publishHandlingInfoIfInteractive(stageExecution);
            return result;

        } catch (NeedUserInteractionException ex) {
            boolean parentBlocking = stepPropsList.contains(OperationStep.Props.CRUCIAL);
            handleQuestion(context, parentExecution, stageExecution, parentBlocking, ex.getQuestion());
            throw ex;
        } catch (OperationCancelledException ex) {
            stageExecution.setStatus(OperationStatus.CANCELLED);
            operationService.save(stageExecution);
            publishHandlingInfoIfInteractive(stageExecution);
            throw ex;
        } catch (Throwable ex) {
            stageExecution.setStatus(OperationStatus.FAILED);
            stageExecution.setErrorMessage(truncateForErrorMessage(ex.getMessage()));
            operationService.save(stageExecution);
            publishHandlingInfoIfInteractive(stageExecution);
            throw ex;
        }
    }

    private OperationExecutionEntity getOrCreateParentExecution(IOperationContext context, OperationStep step) {
        if (context.getOperationId() == null) {
            throw new EntityNotFoundException("Parent operation id is required before stage execution");
        }
        OperationExecutionEntity op = operationService.getOperation(context.getOperationId())
                .orElseThrow(() -> new EntityNotFoundException("Operation not found: " + context.getOperationId()));

        // If a child id is passed by mistake, normalize to root parent.
        if (op.getParentId() != null) {
            OperationExecutionEntity parent = operationService.getOperation(op.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent operation not found: " + op.getParentId()));
            context.setOperationId(parent.getId());
            return parent;
        }
        return op;
    }

    private OperationExecutionEntity getOrCreateStageExecution(IOperationContext context,
                                                               OperationExecutionEntity parentExecution,
                                                               OperationStep step) {
        if (context.getStageExecutionId() != null) {
            OperationExecutionEntity existingStage = operationService.getOperation(context.getStageExecutionId())
                    .orElseThrow(() -> new EntityNotFoundException("Stage execution not found: " + context.getStageExecutionId()));
            if (!parentExecution.getId().equals(existingStage.getParentId())) {
                throw new EntityNotFoundException("Stage execution " + existingStage.getId() + " does not belong to parent " + parentExecution.getId());
            }
            return existingStage;
        }

        OperationExecutionEntity plannedStage = operationService.getChildren(parentExecution.getId()).stream()
                .filter(child -> child.getOperationType() == step.type())
                .filter(child -> child.getStatus() == OperationStatus.PENDING)
                .findFirst()
                .orElse(null);
        if (plannedStage != null) {
            context.setStageExecutionId(plannedStage.getId());
            return plannedStage;
        }

        throw new EntityNotFoundException(
                "Planned stage execution not found for parent "
                        + parentExecution.getId()
                        + " and stage "
                        + step.type());
    }

    private EntityTypeEnum resolveEntityType(IOperationContext context) {
        if (context.getIEntityWithStatus() != null) {
            return context.getIEntityWithStatus().getEntityType();
        }
        if (context instanceof OrderOperationContext) {
            return EntityTypeEnum.ORDER;
        }
        return null;
    }

    private Long resolveStageEntityId(IOperationContext context) {
        if (context instanceof OrderOperationContext orderContext && orderContext.getOrderId() != null) {
            return orderContext.getOrderId();
        }
        if (context instanceof OrderOperationContext) {
            return null;
        }
        return context.getEntityId();
    }

    private void handleQuestion(IOperationContext context,
                                OperationExecutionEntity parentExecution,
                                OperationExecutionEntity stageExecution,
                                boolean parentBlocking,
                                Question<?> question) {
        Long stageEntityId = resolveStageEntityId(context);
        if (stageEntityId != null) {
            stageExecution.setEntityId(stageEntityId);
        }
        EntityTypeEnum stageEntityType = resolveEntityType(context);
        if (stageEntityType != null) {
            stageExecution.setEntityTypeEnum(stageEntityType);
        }

        stageExecution.setStatus(OperationStatus.WAITING_INTERACTION);
        stageExecution.setQuestionType(question.getClass().getSimpleName());
        stageExecution.setQuestionJson(serializeQuestion(question));
        operationService.save(stageExecution);
        publishHandlingInfoIfInteractive(stageExecution);

        if (parentBlocking) {
            parentExecution.setStatus(OperationStatus.WAITING_INTERACTION);
            parentExecution = operationService.save(parentExecution);
            publishHandlingInfoIfInteractive(parentExecution);
        }

        try {
            publisher.publishEvent(new QuestionRequiredEvent(
                    this,
                    parentExecution.getId(),
                    stageExecution.getId(),
                    parentBlocking,
                    question
            ));
        } catch (Throwable listenerError) {
            log.error("QuestionRequiredEvent dispatch failed for parentOpId={} stageExecId={}: {}",
                    parentExecution.getId(),
                    stageExecution.getId(),
                    listenerError.getMessage(),
                    listenerError);
        }
    }

    private boolean isResumeOfWaitingStage(IOperationContext context, OperationExecutionEntity stageExecution) {
        if (context.getStageExecutionId() == null) {
            return false;
        }
        return context.getStageExecutionId().equals(stageExecution.getId())
                && stageExecution.getStatus() == OperationStatus.WAITING_INTERACTION;
    }

    private String serializeQuestion(Question<?> question) {
        try {
            return objectMapper.writeValueAsString(question);
        } catch (Exception ex) {
            log.warn("Failed to serialize question {}: {}", question.getClass().getSimpleName(), ex.getMessage());
            return "{}";
        }
    }

    private void publishHandlingInfoIfInteractive(OperationExecutionEntity execution) {
        if (execution != null && execution.isInteractionEnabled()) {
            publisher.publishEvent(new HandlingInfoEvent(this, execution));
        }
    }

    private String truncateForErrorMessage(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= OPERATION_ERROR_MESSAGE_MAX_LEN) {
            return text;
        }
        String suffix = "...";
        int max = OPERATION_ERROR_MESSAGE_MAX_LEN - suffix.length();
        if (max <= 0) {
            return suffix;
        }
        return text.substring(0, max) + suffix;
    }

}
