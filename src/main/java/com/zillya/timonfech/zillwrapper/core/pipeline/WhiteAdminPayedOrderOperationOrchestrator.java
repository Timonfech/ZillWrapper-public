package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SyntheticInboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.repos.OperationExecutionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhiteAdminPayedOrderOperationOrchestrator implements SourceOrderOperationOrchestrator {

    private static final Set<OperationStatus> ACTIVE_STATUSES = Set.of(
            OperationStatus.RUNNING,
            OperationStatus.PAUSE,
            OperationStatus.RESUME,
            OperationStatus.WAITING_INTERACTION
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final OperationExecutionRepository operationExecutionRepository;
    private final OperationExecutionService operationExecutionService;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final OperationGraphRegistry operationGraphRegistry;
    private final PipelineDispatcher pipelineDispatcher;

    @Override
    public boolean supports(EntityUpdatedEvent event, SourceEntity source) {
        return event.getEntityId() != null
                && source.getType() == SourceType.WHITE_ADMIN;
    }

    @Override
    public void start(EntityUpdatedEvent event, SourceEntity source) {
        var order = orderRepository.findByIdWithDeliveryTargets(event.getEntityId()).orElse(null);
        if (order == null) {
            log.info("PAYED orchestrator skip: order not found orderId={}", event.getEntityId());
            return;
        }
        if (order.getOrderStatus() != OrderStatus.PAYED) {
            log.info("PAYED orchestrator skip: order is not PAYED orderId={} status={}",
                    order.getId(), order.getOrderStatus());
            return;
        }
        if (orderItemRepository.findByOrderId(order.getId()).isEmpty()) {
            log.info("PAYED orchestrator skip: order has no items orderId={}", order.getId());
            return;
        }
        boolean hasEnabledTargets = order.getDeliveryTargets().stream().anyMatch(t -> t.isEnabled());
        if (!hasEnabledTargets) {
            log.info("PAYED orchestrator skip: order has no enabled delivery targets orderId={}", order.getId());
            return;
        }

        if (!canStartForOrder(order.getId())) {
            return;
        }

        Optional<UserEntity> admin = userRepository.findActiveAdmins().stream().findFirst();
        if (admin.isEmpty()) {
            log.warn("PAYED orchestrator skip: no active admin found for initiator. orderId={}", order.getId());
            return;
        }

        OrderOperationContext ctx = new OrderOperationContext(
                source.getId(),
                null,
                "",
                List.of(),
                List.of(),
                new SyntheticInboundEvent(source)
        );
        ctx.setInitiatorUserId(admin.get().getId());
        ctx.setOrderId(order.getId());
        ctx.setCurrentStage(OperationType.ORDER_UPDATE);
        ctx.setPayedReady(true);
        ExecutionPlan executionPlan = operationGraphRegistry.buildExecutionPlan(OperationType.LICENSE_FULFILLMENT, ctx);
        ctx.replacePipelinePlan(executionPlan.steps().stream().map(step -> step.stageType()).toList());

        operationExecutionService.createParentOperation(ctx, OperationType.LICENSE_FULFILLMENT);
        runtimeRegistry.createForOperation(ctx.getOperationId(), ctx);
        operationExecutionService.ensurePlannedStages(ctx.getOperationId(), ctx, executionPlan);
        pipelineDispatcher.dispatch(ctx);
        log.info("PAYED orchestrator started ORDER_UPDATE pipeline for orderId={} parentOpId={}",
                order.getId(),
                ctx.getOperationId());
    }

    private boolean canStartForOrder(Long orderId) {
        List<OperationExecutionEntity> stages = operationExecutionRepository
                .findByEntityIdAndOperationTypeOrderByCreatedAtDesc(orderId, OperationType.ORDER_UPDATE);
        if (stages.isEmpty()) {
            return true;
        }
        OperationExecutionEntity latestStage = stages.getFirst();
        OperationExecutionEntity root = latestStage.getParentId() != null
                ? operationExecutionService.getOperation(latestStage.getParentId()).orElse(null)
                : latestStage;
        if (root == null) {
            return true;
        }
        OperationStatus rootStatus = root.getStatus();
        if (ACTIVE_STATUSES.contains(rootStatus) || rootStatus == OperationStatus.DONE) {
            log.info("PAYED orchestrator skip: duplicate/active processing orderId={} rootOpId={} status={}",
                    orderId,
                    root.getId(),
                    rootStatus);
            return false;
        }
        return true;
    }
}
