package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.communication.EmailNotifyService;
import com.zillya.timonfech.zillwrapper.core.communication.EmailSendResult;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.security.OrderSecurityService;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyHandler implements OperationHandler<IOperationContext> {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderSecurityService orderSecurityService;
    private final EmailNotifyService emailNotifyService;
    private final OperationExecutionService operationExecutionService;

    @Override
    public String name() {
        return "NOTIFY";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.NOTIFY;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.NOTIFY && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.NOTIFY, stepProps = {OperationStep.Props.FINAL})
    @Override
    public OperationResult<?> handle(IOperationContext context) throws OperationCancelledException {
        OrderOperationContext orderCtx = asOrderContext(context).orElse(null);
        if (orderCtx == null) {
            return OperationResult.fail("NOTIFY requires OrderOperationContext", false);
        }

        Long orderId = orderCtx.getOrderId();
        if (orderId == null) {
            return OperationResult.fail("Cannot notify without orderId", false);
        }

        log.info("Starting notify stage for operation={} order={}", orderCtx.getOperationId(), orderId);

        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElse(null);
        if (order != null) {
            for (var target : order.getDeliveryTargets()) {
                if (!target.isEnabled()) {
                    continue;
                }
                ContactMethodType type = target.getContactMethod() != null
                        ? target.getContactMethod().getType()
                        : null;
                if (type == null && target.getContactMethod() instanceof com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact) {
                    type = ContactMethodType.EMAIL;
                }
                log.info("Delivery target order={} contactType={} output={}",
                        orderId,
                        type,
                        target.getOutputFormat());
            }
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        Long scopedOrderItemId = parseLongToken(orderCtx.getCommandPayload(), "rid_order_item_id");
        if (scopedOrderItemId != null) {
            items = items.stream().filter(i -> scopedOrderItemId.equals(i.getId())).toList();
        }
        boolean resendFlow = isResendFlow(orderCtx);
        List<OrderItemEntity> readyItems = items.stream()
                .filter(item -> isReadyForNotify(item, resendFlow))
                .toList();
        for (OrderItemEntity item : items) {
            if (readyItems.stream().anyMatch(ri -> ri.getId().equals(item.getId()))) {
                log.info("Notify item eligible orderId={} itemId={} status={} outputTypes={}",
                        orderId,
                        item.getId(),
                        item.getProcessingStatus(),
                        item.getOutputTypes());
            } else {
                log.info("Notify item skipped orderId={} itemId={} status={} outputTypes={} reason={}",
                        orderId,
                        item.getId(),
                        item.getProcessingStatus(),
                        item.getOutputTypes(),
                        describeNotReadyReason(item, resendFlow));
            }
        }
        if (readyItems.isEmpty()) {
            String details = items.stream()
                    .map(item -> "#" + item.getId() + "[" + item.getProcessingStatus() + "]:" + describeNotReadyReason(item, resendFlow))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("no_items");
            return OperationResult.fail("No items are ready for notification for order " + orderId + " | " + details, false);
        }

        log.info("Notify eligible items orderId={} resendFlow={} totalItems={} eligibleItems={}",
                orderId,
                resendFlow,
                items.size(),
                readyItems.size());

        Locale locale = resolveLocale(orderCtx, order);
        OperationType rootOperationType = resolveRootOperationType(orderCtx);
        EmailSendResult sendResult = emailNotifyService.sendOrderItems(
                order,
                readyItems,
                orderCtx.getArtifacts(),
                resolveItemArtifacts(orderCtx),
                rootOperationType,
                locale
        );
        Set<Long> deliveredItemIds = sendResult.getDeliveredItemIds();
        Set<Long> failedItemIds = sendResult.getFailedItemIds();
        log.info("Notify send result orderId={} deliveredItems={} failedItems={} errors={}",
                orderId,
                deliveredItemIds,
                failedItemIds,
                sendResult.getErrors());

        List<Long> successItemIds = new ArrayList<>(deliveredItemIds);
        boolean allSuccess = !items.isEmpty();
        boolean anySuccess = !deliveredItemIds.isEmpty();
        for (OrderItemEntity item : items) {
            if (deliveredItemIds.contains(item.getId())) {
                item.setProcessingStatus(ItemProcessingStatus.DELIVERED);
            } else if (failedItemIds.contains(item.getId())) {
                item.setProcessingStatus(ItemProcessingStatus.FAILED);
                allSuccess = false;
            } else if (item.getProcessingStatus() == ItemProcessingStatus.FAILED) {
                allSuccess = false;
            } else if (item.getProcessingStatus() != ItemProcessingStatus.DELIVERED) {
                allSuccess = false;
            }
            orderItemRepository.save(item);
        }

        if (order != null) {
            if (allSuccess) {
                order.setOrderStatus(OrderStatus.SENT);
            } else if (anySuccess) {
                order.setOrderStatus(OrderStatus.PROCESSED);
            } else {
                order.setOrderStatus(OrderStatus.FAILED);
            }
            orderRepository.save(order);
        }

        if (anySuccess && orderCtx.getInitiatorUserId() != null) {
            orderSecurityService.confirmQuota(orderCtx.getInitiatorUserId(), orderId);
        }
        if (!sendResult.getErrors().isEmpty()) {
            String joined = String.join("; ", sendResult.getErrors());
            return OperationResult.fail(joined, false);
        }
        if (deliveredItemIds.isEmpty()) {
            return OperationResult.fail("No e-mails were delivered for order " + orderId, false);
        }

        return OperationResult.ok(null);
    }

    private Locale resolveLocale(OrderOperationContext context, OrderEntity order) {
        if (context.getLocaleTag() != null && !context.getLocaleTag().isBlank()) {
            return Locale.forLanguageTag(context.getLocaleTag());
        }
        if (order != null && order.getClient() != null && order.getClient().getLocale() != null) {
            return order.getClient().getLocale();
        }
        return Locale.forLanguageTag("uk");
    }

    private boolean isResendFlow(OrderOperationContext context) {
        if (context.getOperationId() == null) {
            return false;
        }
        return operationExecutionService.getRootOperation(context.getOperationId())
                .map(root -> root.getOperationType() == OperationType.RESEND_NOTIFICATION)
                .orElse(false);
    }

    private OperationType resolveRootOperationType(OrderOperationContext context) {
        if (context.getOperationId() == null) {
            return OperationType.NOTIFY;
        }
        return operationExecutionService.getRootOperation(context.getOperationId())
                .map(root -> root.getOperationType())
                .orElse(OperationType.NOTIFY);
    }

    private boolean isReadyForNotify(OrderItemEntity item, boolean resendFlow) {
        if (item == null || item.getProcessingStatus() == null) {
            return false;
        }
        if (item.getProcessingStatus() == ItemProcessingStatus.ARTIFACTS_READY) {
            return true;
        }
        if (resendFlow && (item.getProcessingStatus() == ItemProcessingStatus.DELIVERED
                || item.getProcessingStatus() == ItemProcessingStatus.DELIVERY_FAILED)) {
            return true;
        }
        return item.getProcessingStatus() == ItemProcessingStatus.GENERATED && !requiresArtifacts(item);
    }

    private boolean requiresArtifacts(OrderItemEntity item) {
        return item.getOutputTypes() != null && item.getOutputTypes().contains(OutputType.EXCEL);
    }

    private String describeNotReadyReason(OrderItemEntity item, boolean resendFlow) {
        if (item == null || item.getProcessingStatus() == null) {
            return "status_missing";
        }
        if (item.getProcessingStatus() == ItemProcessingStatus.ARTIFACTS_READY) {
            return "eligible";
        }
        if (resendFlow && (item.getProcessingStatus() == ItemProcessingStatus.DELIVERED
                || item.getProcessingStatus() == ItemProcessingStatus.DELIVERY_FAILED)) {
            return "eligible_resend";
        }
        if (item.getProcessingStatus() == ItemProcessingStatus.GENERATED && requiresArtifacts(item)) {
            return "excel_requested_artifacts_not_ready";
        }
        if (item.getProcessingStatus() == ItemProcessingStatus.GENERATED) {
            return "eligible_text";
        }
        return "status_not_ready_" + item.getProcessingStatus();
    }

    private Map<Long, List<IArtifact>> resolveItemArtifacts(OrderOperationContext orderCtx) {
        if (orderCtx.getItemArtifacts() == null || orderCtx.getItemArtifacts().isEmpty()) {
            return Map.of();
        }
        return orderCtx.getItemArtifacts();
    }

    private Long parseLongToken(String payload, String key) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*(\\d+)(?:\\s|$)")
                .matcher(payload);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (Exception ex) {
            return null;
        }
    }
}
