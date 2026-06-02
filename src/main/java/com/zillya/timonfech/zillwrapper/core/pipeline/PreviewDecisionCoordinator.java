package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientType;
import com.zillya.timonfech.zillwrapper.core.events.OrderPreviewPayload;
import com.zillya.timonfech.zillwrapper.core.events.OrderPreviewRequestedEvent;
import com.zillya.timonfech.zillwrapper.core.events.PreviewItem;
import com.zillya.timonfech.zillwrapper.core.pending.OrderPreviewPendingItem;
import com.zillya.timonfech.zillwrapper.core.pending.OrderPreviewPendingPayload;
import com.zillya.timonfech.zillwrapper.core.pending.PendingTaskService;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.ContactManagementService;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PreviewDecisionCoordinator {
    private static final Pattern EXPLICIT_LOCALE_FLAG_PATTERN = Pattern.compile("(?i)(?:^|\\s)--?(?:l|locale)\\s*(?:=|:|\\s)\\s*([a-z]{2,8}(?:-[a-z0-9]{2,8})*)");
    private final PendingTaskService pendingTaskService;
    private final OperationExecutionService operationExecutionService;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final OperationGraphRegistry operationGraphRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ContactManagementService contactManagementService;

    public void publishPreview(TelegramInboundEvent event, OrderOperationContext orderCtx, Long userId) {
        Instant now = Instant.now();
        orderCtx.setInitiatorUserId(userId);
        applyPartnerSubscriptionDefault(orderCtx);
        if (orderCtx.getOperationId() == null) {
            operationExecutionService.createParentOperation(orderCtx, com.zillya.timonfech.zillwrapper.core.entities.OperationType.LICENSE_FULFILLMENT);
        }
        ExecutionPlan executionPlan = operationGraphRegistry.buildExecutionPlan(
                com.zillya.timonfech.zillwrapper.core.entities.OperationType.LICENSE_FULFILLMENT,
                orderCtx
        );
        orderCtx.replacePipelinePlan(executionPlan.steps().stream().map(step -> step.stageType()).toList());
        operationExecutionService.ensurePlannedStages(orderCtx.getOperationId(), orderCtx, executionPlan);
        runtimeRegistry.createForOperation(orderCtx.getOperationId(), orderCtx);
        String sourceActorId = event.getPayload().getMessage() != null && event.getPayload().getMessage().getFrom() != null
                ? event.getPayload().getMessage().getFrom().getId().toString()
                : null;
        OrderPreviewPendingPayload pendingPayload = new OrderPreviewPendingPayload(
                orderCtx.getSourceId(),
                orderCtx.getPortalId(),
                orderCtx.getWhiteAdminId(),
                orderCtx.getUserComment(),
                orderCtx.getEmail(),
                orderCtx.getEmails(),
                orderCtx.getLocaleTag(),
                orderCtx.isPayedReady(),
                orderCtx.getWhiteAdminId() == null && orderCtx.getUserComment() != null && !orderCtx.getUserComment().isBlank(),
                orderCtx.getWaDocAddress(),
                orderCtx.getWaComment(),
                java.util.List.<OrderPreviewPendingItem>of(),
                java.util.List.of(),
                null
        );
        PendingTaskEntity task = pendingTaskService.create(
                PendingTaskType.ORDER_PREVIEW_CONFIRMATION,
                orderCtx.getSourceId(),
                userId,
                sourceActorId,
                pendingPayload
        );
        Instant expiresAt = task.getExpiresAt();
        Integer sourceMessageId = event.getPayload().getMessage() != null ? event.getPayload().getMessage().getMessageId() : null;
        String rawText = event.getPayload().getMessage() != null ? event.getPayload().getMessage().getText() : null;
        boolean localeExplicit = hasExplicitLocaleFlag(rawText);
        String explicitLocaleTag = orderCtx.getLocaleTag();
        OrderPreviewPayload payload = new OrderPreviewPayload(
                orderCtx.getPortalId(),
                orderCtx.getWhiteAdminId(),
                orderCtx.getUserComment(),
                orderCtx.getEmail(),
                orderCtx.getEmails(),
                explicitLocaleTag,
                localeExplicit,
                orderCtx.getWhiteAdminId() == null && orderCtx.getUserComment() != null && !orderCtx.getUserComment().isBlank(),
                orderCtx.getWaDocAddress(),
                orderCtx.getWaComment(),
                orderCtx.getItemSpecs().stream()
                        .map(spec -> new PreviewItem(
                                spec.product().brandId(),
                                spec.product().productId(),
                                spec.product().names().getOrDefault("en_short", "Product"),
                                spec.count(),
                                spec.computers(),
                                spec.period(),
                                spec.keyTypes(),
                                spec.outputTypes(),
                                spec.subscribed(),
                                spec.options()
                        ))
                        .toList(),
                orderCtx.getDeliveryTargets(),
                null
        );
        eventPublisher.publishEvent(new OrderPreviewRequestedEvent(
                this,
                orderCtx.getOperationId(),
                event.getPayload().getMessage().getChatId(),
                sourceMessageId,
                userId,
                task.getTaskId(),
                payload,
                now,
                expiresAt,
                event
        ));
    }

    private boolean hasExplicitLocaleFlag(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        return EXPLICIT_LOCALE_FLAG_PATTERN.matcher(rawText).find();
    }

    private void applyPartnerSubscriptionDefault(OrderOperationContext orderCtx) {
        if (orderCtx == null || orderCtx.getItemSpecs() == null || orderCtx.getItemSpecs().isEmpty()) {
            return;
        }
        Boolean explicit = orderCtx.getPartnerOverride();
        boolean isPartner = explicit != null ? explicit : isExistingPartnerByEmail(orderCtx.primaryEmail());
        if (!isPartner) {
            return;
        }
        var adjusted = orderCtx.getItemSpecs().stream()
                .map(spec -> {
                    boolean subscribeExplicit = spec.options() != null && Boolean.TRUE.equals(spec.options().subscribeExplicit());
                    if (subscribeExplicit) {
                        return spec;
                    }
                    return new OrderItemSpec(
                            spec.product(),
                            spec.count(),
                            spec.period(),
                            spec.computers(),
                            spec.outputTypes(),
                            spec.keyTypes(),
                            false,
                            spec.options()
                    );
                })
                .toList();
        orderCtx.getItemSpecs().clear();
        orderCtx.getItemSpecs().addAll(adjusted);
    }

    private boolean isExistingPartnerByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        try {
            return contactManagementService.getEmailContactByEmail(email.trim())
                    .map(c -> c.getClient() != null && c.getClient().getClientType() == ClientType.PARTNER)
                    .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }
}
