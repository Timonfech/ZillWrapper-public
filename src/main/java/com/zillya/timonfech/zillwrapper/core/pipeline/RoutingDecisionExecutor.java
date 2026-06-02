package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramControlMessageService;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.events.InboundErrorCategory;
import com.zillya.timonfech.zillwrapper.core.events.InboundProcessingErrorEvent;
import com.zillya.timonfech.zillwrapper.core.events.OrderPreviewDecisionEvent;
import com.zillya.timonfech.zillwrapper.core.events.PendingTaskDecisionRequestedEvent;
import com.zillya.timonfech.zillwrapper.core.pending.PendingTaskExecutor;
import com.zillya.timonfech.zillwrapper.core.routing.RoutingDecision;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingDecisionExecutor {

    public enum DecisionExecutionResult {
        ROUTED,
        IGNORED,
        ERROR
    }

    private final PipelineDispatcher pipelineDispatcher;
    private final PreviewDecisionCoordinator previewDecisionCoordinator;
    private final InteractiveDecisionCoordinator interactiveDecisionCoordinator;
    private final ApplicationEventPublisher eventPublisher;
    private final PendingTaskExecutor pendingTaskExecutor;
    private final OperationExecutionService operationExecutionService;
    private final TelegramControlMessageService telegramControlMessageService;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final OperationGraphRegistry operationGraphRegistry;

    public DecisionExecutionResult execute(InboundEvent<?> event, UserEntity user, RoutingDecision decision) {
        if (decision instanceof RoutingDecision.IgnoreDecision) {
            return DecisionExecutionResult.IGNORED;
        }
        if (decision instanceof RoutingDecision.RoutingErrorDecision errorDecision) {
            publishInboundError(event, errorDecision.category(), errorDecision.safeMessage(), errorDecision.internalMessage(), errorDecision.cause());
            return DecisionExecutionResult.ERROR;
        }
        if (decision instanceof RoutingDecision.PreviewDecision previewDecision) {
            previewDecisionCoordinator.publishPreview(previewDecision.event(), previewDecision.context(), user.getId());
            return DecisionExecutionResult.ROUTED;
        }
        if (decision instanceof RoutingDecision.SearchDecision
                || decision instanceof RoutingDecision.MenuDecision
                || decision instanceof RoutingDecision.InteractionDecision
                || decision instanceof RoutingDecision.ControlCallbackDecision) {
            return interactiveDecisionCoordinator.handle(decision, user);
        }
        if (decision instanceof RoutingDecision.StartPipelineDecision startPipelineDecision) {
            startPipeline(startPipelineDecision.context(), user);
            return DecisionExecutionResult.ROUTED;
        }
        return DecisionExecutionResult.IGNORED;
    }

    private void startPipeline(com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext context, UserEntity user) {
        context.setInitiatorUserId(user.getId());
        com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan executionPlan = null;
        if (context instanceof OrderOperationContext orderCtx) {
            executionPlan = operationGraphRegistry.buildExecutionPlan(resolveRootType(context), orderCtx);
            orderCtx.replacePipelinePlan(executionPlan.steps().stream().map(step -> step.stageType()).toList());
        }
        if (context.getOperationId() == null) {
            OperationType rootType = resolveRootType(context);
            operationExecutionService.createParentOperation(context, rootType);
            if (context.getSourceContext() instanceof TelegramInboundEvent tgEvent && context.getOperationId() != null) {
                telegramControlMessageService.ensureBindingForTelegramOperation(context.getOperationId(), tgEvent);
            }
            if (context instanceof OrderOperationContext orderCtx && context.getOperationId() != null) {
                runtimeRegistry.createForOperation(context.getOperationId(), orderCtx);
                operationExecutionService.ensurePlannedStages(context.getOperationId(), orderCtx, executionPlan);
            }
        }
        if (context.getOperationId() == null || operationExecutionService.getRootOperation(context.getOperationId()).isEmpty()) {
            throw new IllegalStateException("Pipeline dispatch requires existing parent operation");
        }
        pipelineDispatcher.dispatch(context);
    }

    private OperationType resolveRootType(com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext context) {
        if (context instanceof OrderOperationContext orderCtx
                && (orderCtx.getCurrentStage() == OperationType.ORDER_CREATION
                || orderCtx.getCurrentStage() == OperationType.ORDER_UPDATE)) {
            return OperationType.LICENSE_FULFILLMENT;
        }
        return context.getOperationType();
    }

    @EventListener
    public void onPendingTaskDecision(PendingTaskDecisionRequestedEvent event) {
        String sourceActorId = event.getSourceActorId() != null
                ? event.getSourceActorId()
                : (event.getActorUserId() == null ? null : event.getActorUserId().toString());
        if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.CONFIRM) {
            pendingTaskExecutor.confirm(event.getTaskId(), sourceActorId, event.getChatId(), event.getMessageId());
        } else if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_YES) {
            pendingTaskExecutor.applyWaPlaceholderDecision(event.getTaskId(), true, event.getActorUserId(), event.getChatId(), event.getMessageId());
        } else if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_NO) {
            pendingTaskExecutor.applyWaPlaceholderDecision(event.getTaskId(), false, event.getActorUserId(), event.getChatId(), event.getMessageId());
        } else if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_AND_CONFIRM) {
            pendingTaskExecutor.confirmWithWaPlaceholderDecision(event.getTaskId(), true, event.getActorUserId(), sourceActorId, event.getChatId(), event.getMessageId());
        } else if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.WA_SKIP_AND_CONFIRM) {
            pendingTaskExecutor.confirmWithWaPlaceholderDecision(event.getTaskId(), false, event.getActorUserId(), sourceActorId, event.getChatId(), event.getMessageId());
        } else if (event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.CANCEL
                || event.getDecision() == PendingTaskDecisionRequestedEvent.Decision.EXPIRE) {
            pendingTaskExecutor.cancel(event.getTaskId());
        }
    }

    /**
     * Backward-compatible adapter for old preview event producers.
     */
    @Deprecated
    @EventListener
    public void onPreviewDecision(OrderPreviewDecisionEvent event) {
        PendingTaskDecisionRequestedEvent.Decision decision = switch (event.getDecision()) {
            case CONFIRM -> PendingTaskDecisionRequestedEvent.Decision.CONFIRM;
            case CANCEL -> PendingTaskDecisionRequestedEvent.Decision.CANCEL;
            case EXPIRED -> PendingTaskDecisionRequestedEvent.Decision.EXPIRE;
        };
        eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                this,
                event.getPreviewId(),
                decision,
                null,
                event.getUserId() == null ? null : event.getUserId().toString(),
                event.getChatId(),
                null,
                event.getDecidedAt()
        ));
    }


    private void publishInboundError(InboundEvent<?> event,
                                     InboundErrorCategory category,
                                     String safeMessage,
                                     String internalMessage,
                                     Throwable cause) {
        Long chatId = null;
        Long userId = null;
        if (event instanceof TelegramInboundEvent tgEvent) {
            if (tgEvent.getPayload().hasMessage()) {
                chatId = tgEvent.getPayload().getMessage().getChatId();
                if (tgEvent.getPayload().getMessage().getFrom() != null) {
                    userId = tgEvent.getPayload().getMessage().getFrom().getId();
                }
            } else if (tgEvent.getPayload().hasCallbackQuery()) {
                if (tgEvent.getPayload().getCallbackQuery().getMessage() != null) {
                    chatId = tgEvent.getPayload().getCallbackQuery().getMessage().getChatId();
                }
                if (tgEvent.getPayload().getCallbackQuery().getFrom() != null) {
                    userId = tgEvent.getPayload().getCallbackQuery().getFrom().getId();
                }
            }
        }

        SourceEntity sourceEntity = event.getSourceEntity();
        eventPublisher.publishEvent(new InboundProcessingErrorEvent(
                this,
                event.getId(),
                sourceEntity.getSourceType(),
                sourceEntity.getId(),
                chatId,
                userId,
                null,
                category,
                safeMessage,
                internalMessage,
                cause
        ));
    }

}
