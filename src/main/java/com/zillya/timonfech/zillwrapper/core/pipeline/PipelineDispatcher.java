package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionKind;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.interfaces.AsyncOperationHandler;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of pipeline steps.
 * Can be called directly or triggered via events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineDispatcher {
    private final List<OperationHandler<IOperationContext>> handlers;
    private final OperationExecutionService operationExecutionService;
    private final OrderRepository orderRepository;
    private final AsyncStageCoordinator asyncStageCoordinator;
    private final ObjectMapper objectMapper;

    /**
     * Executes the appropriate handler for the current stage defined in the context.
     */
    public void dispatch(IOperationContext context) {
        if (!(context instanceof OrderOperationContext orderCtx)) {
            dispatchSingle(context);
            return;
        }

        Long activeParentVersion = null;
        List<OperationType> activePlanOrder = List.of();
        while (true) {
            BigInteger parentId = orderCtx.getOperationId();
            if (parentId == null) {
                throw new IllegalStateException("Cannot dispatch order pipeline without parent operation id");
            }

            OperationExecutionEntity parent = operationExecutionService.getRootOperation(parentId).orElse(null);
            if (parent == null) {
                throw new IllegalStateException("Parent operation not found: " + parentId);
            }
            if (!java.util.Objects.equals(activeParentVersion, parent.getStateVersion())) {
                activeParentVersion = parent.getStateVersion();
                activePlanOrder = parsePlanOrder(parent.getExecutionPlanJson());
            }

            OperationStatus parentStatus = parent.getStatus();
            if (parentStatus == OperationStatus.PAUSE
                    || parentStatus == OperationStatus.CANCELLED
                    || parentStatus == OperationStatus.WAITING_INTERACTION
                    || parentStatus == OperationStatus.DONE
                    || parentStatus == OperationStatus.PARTIALLY_DONE
                    || parentStatus == OperationStatus.FAILED) {
                log.info("Stop pipeline dispatch for parent {} because status is {}", parentId, parentStatus);
                return;
            }

            List<OperationExecutionEntity> children = operationExecutionService.getChildren(parentId);
            List<OperationExecutionEntity> plannedStages = children.stream()
                    .filter(child -> child.getExecutionKind() == OperationExecutionKind.STAGE)
                    .toList();
            List<OperationExecutionEntity> orderedStages = orderByPlan(plannedStages, activePlanOrder);
            if (plannedStages.isEmpty()) {
                throw new IllegalStateException("No prebuilt stage plan found for parent operation " + parentId);
            }
            log.debug("Pipeline parent={} status={} children=[{}]",
                    parentId,
                    parentStatus,
                    describeChildren(orderedStages));
            if (!orderedStages.isEmpty() && orderedStages.stream().allMatch(this::isCompletedStage)) {
                operationExecutionService.markParentDone(parentId);
                return;
            }

            OperationType current = nextPlannedRunnable(orderedStages).orElse(null);
            if (current == null) {
                log.info("No runnable stage for parent {}. reason={} children=[{}]",
                        parentId,
                        resolveStopReason(orderedStages),
                        describeChildren(orderedStages));
                return;
            }
            if (current == OperationType.LICENSE_GENERATION && !isPayedAndReady(orderCtx)) {
                String reason = "Pipeline blocked: payment not confirmed (awaiting payment)";
                log.info("Pipeline blocked: payment not confirmed. parentOpId={} orderId={} orderStatus={}",
                        parentId,
                        orderCtx.getOrderId(),
                        resolveOrderStatus(orderCtx.getOrderId()));
                operationExecutionService.markParentDone(parentId, reason);
                return;
            }
            orderCtx.setCurrentStage(current);
            orderCtx.setStageExecutionId(null);
            log.debug("Dispatching operation type {} for parent {}", current, parentId);

            OperationResult<?> result = dispatchSingle(orderCtx);
            if (result == null) {
                return;
            }

            if (!result.isSuccess()) {
                log.error("Pipeline stage failed parent={} stage={} recoverable={} error={}",
                        parentId,
                        current,
                        result.isRecoverable(),
                        result.getErrorMessage());
                operationExecutionService.markParentFailed(parentId, result.getErrorMessage());
                return;
            }
            operationExecutionService.unblockParentIfNoCrucialWaiting(parentId);
        }
    }

    private OperationResult<?> dispatchSingle(IOperationContext context) {
        OperationResult<?> precondition = validateContextPreconditions(context);
        if (precondition != null) {
            return precondition;
        }
        return handlers.stream()
                .filter(h -> h.supports(context))
                .findFirst()
                .map(h -> executeHandler(h, context))
                .orElseGet(() -> {
                    log.warn("No handler found for operation type: {}", context.getOperationType());
                    return null;
                });
    }

    private OperationResult<?> validateContextPreconditions(IOperationContext context) {
        if (context == null || context.getOperationType() == null) {
            return OperationResult.fail("Operation context/type is missing", false);
        }
        OperationType operationType = context.getOperationType();
        List<OperationHandler<IOperationContext>> operationHandlers = handlers.stream()
                .filter(handler -> operationType.equals(handler.handledOperationType()))
                .toList();
        if (operationHandlers.isEmpty()) {
            return null;
        }
        boolean contextMatch = operationHandlers.stream()
                .anyMatch(handler -> handler.requiredContextType().isInstance(context));
        if (!contextMatch) {
            String expected = operationHandlers.stream()
                    .map(handler -> handler.requiredContextType().getSimpleName())
                    .distinct()
                    .collect(Collectors.joining("|"));
            return OperationResult.fail(operationType + " requires context type: " + expected, false);
        }
        return null;
    }

    private OperationResult<?> executeHandler(OperationHandler<IOperationContext> handler, IOperationContext context) {
        try {
            if (handler instanceof AsyncOperationHandler<IOperationContext> asyncHandler) {
                var stageFuture = asyncHandler.handleAsync(context);
                boolean crucial = isCurrentStageCrucial(context.getStageExecutionId());
                if (!(context instanceof OrderOperationContext orderContext)) {
                    log.error("Async handler {} requires OrderOperationContext, got {}",
                            handler.name(),
                            context.getClass().getSimpleName());
                    return OperationResult.fail("Async stage context type is invalid", false);
                }
                asyncStageCoordinator.attach(orderContext, context.getOperationType(), stageFuture, crucial);
                if (crucial) {
                    return null;
                }
                return OperationResult.ok(null);
            }
            return handler.handle(context);
        } catch (NeedUserInteractionException ex) {
            log.info("Handler {} requires user interaction. Pipeline paused.", handler.name());
            return null;
        } catch (OperationCancelledException ex) {
            log.info("Handler {} cancelled by user action.", handler.name());
            return null;
        } catch (Exception ex) {
            log.error("Handler {} failed: {}", handler.name(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private boolean isCurrentStageCrucial(BigInteger stageExecutionId) {
        if (stageExecutionId == null) {
            return true;
        }
        return operationExecutionService.getOperation(stageExecutionId)
                .map(stage -> !stage.isCancelable())
                .orElse(true);
    }

    private String describeChildren(List<OperationExecutionEntity> children) {
        return children.stream()
                .map(child -> child.getOperationType()
                        + ":"
                        + child.getStatus()
                        + ":cancelable="
                        + child.isCancelable())
                .collect(Collectors.joining(", "));
    }

    private String resolveStopReason(List<OperationExecutionEntity> children) {
        boolean hasFailedOrCancelled = children.stream()
                .anyMatch(child -> child.getStatus() == OperationStatus.FAILED
                        || child.getStatus() == OperationStatus.CANCELLED);
        if (hasFailedOrCancelled) {
            return "failed_or_cancelled_child";
        }
        boolean hasActive = children.stream()
                .anyMatch(child -> (child.getStatus() == OperationStatus.RUNNING
                        || child.getStatus() == OperationStatus.PAUSE
                        || child.getStatus() == OperationStatus.RESUME
                        || child.getStatus() == OperationStatus.WAITING_INTERACTION)
                        && isCrucial(child));
        if (hasActive) {
            return "active_child_in_progress";
        }
        return "no_runnable_stage";
    }

    private java.util.Optional<OperationType> nextPlannedRunnable(List<OperationExecutionEntity> plannedStages) {
        if (plannedStages.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (int i = 0; i < plannedStages.size(); i++) {
            OperationExecutionEntity stage = plannedStages.get(i);
            OperationStatus status = stage.getStatus();
            if (isCompletedStage(stage)) {
                continue;
            }
            if (status == OperationStatus.FAILED || status == OperationStatus.CANCELLED) {
                return java.util.Optional.empty();
            }
            if (isActiveStatus(status)) {
                if (!stage.isCancelable() && !stage.isNonBlocking()) {
                    return java.util.Optional.empty();
                }
                continue;
            }
            if (status == OperationStatus.PENDING) {
                if (i == 0) {
                    return java.util.Optional.of(stage.getOperationType());
                }
                OperationExecutionEntity prev = plannedStages.get(i - 1);
                if (isCompletedStage(prev)
                        || (isActiveStatus(prev.getStatus()) && (prev.isCancelable() || prev.isNonBlocking()))) {
                    return java.util.Optional.of(stage.getOperationType());
                }
                return java.util.Optional.empty();
            }
            return java.util.Optional.empty();
        }
        return java.util.Optional.empty();
    }

    private boolean isCompletedStage(OperationExecutionEntity stage) {
        return stage.getStatus() == OperationStatus.DONE || stage.getStatus() == OperationStatus.PARTIALLY_DONE;
    }

    private boolean isActiveStatus(OperationStatus status) {
        return status == OperationStatus.RUNNING
                || status == OperationStatus.PAUSE
                || status == OperationStatus.RESUME
                || status == OperationStatus.WAITING_INTERACTION;
    }

    /**
     * In current lifecycle persistence, CRUCIAL stage is represented by non-cancelable flag.
     */
    private boolean isCrucial(OperationExecutionEntity child) {
        return !child.isCancelable();
    }

    private boolean isPayedAndReady(OrderOperationContext context) {
        if (context.getOrderId() == null) {
            return false;
        }
        return orderRepository.findById(context.getOrderId())
                .map(order -> order.getOrderStatus() == OrderStatus.PAYED)
                .orElse(false);
    }

    private OrderStatus resolveOrderStatus(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderRepository.findById(orderId)
                .map(com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity::getOrderStatus)
                .orElse(null);
    }

    private List<OperationType> parsePlanOrder(String executionPlanJson) {
        if (executionPlanJson == null || executionPlanJson.isBlank()) {
            return List.of();
        }
        try {
            ExecutionPlan plan = objectMapper.readValue(executionPlanJson, ExecutionPlan.class);
            if (plan == null || plan.steps() == null) {
                return List.of();
            }
            return plan.steps().stream()
                    .sorted(java.util.Comparator.comparingInt(step -> step.sequenceNo()))
                    .map(step -> step.stageType())
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to parse execution plan json: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<OperationExecutionEntity> orderByPlan(List<OperationExecutionEntity> stages, List<OperationType> planOrder) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        if (planOrder == null || planOrder.isEmpty()) {
            return stages;
        }
        Map<OperationType, List<OperationExecutionEntity>> grouped = new HashMap<>();
        for (OperationExecutionEntity stage : stages) {
            grouped.computeIfAbsent(stage.getOperationType(), ignored -> new ArrayList<>()).add(stage);
        }
        List<OperationExecutionEntity> ordered = new ArrayList<>();
        for (OperationType type : planOrder) {
            List<OperationExecutionEntity> bucket = grouped.get(type);
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            ordered.add(bucket.removeFirst());
        }
        for (List<OperationExecutionEntity> leftovers : grouped.values()) {
            ordered.addAll(leftovers);
        }
        return ordered;
    }

}
