package com.zillya.timonfech.zillwrapper.core.pending;

import com.zillya.timonfech.zillwrapper.core.communication.InteractionBindingService;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramPreviewStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskStatus;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.PhoneContact;
import com.zillya.timonfech.zillwrapper.core.events.OrderPreviewPayload;
import com.zillya.timonfech.zillwrapper.core.events.PendingTaskCompletedEvent;
import com.zillya.timonfech.zillwrapper.core.events.PendingTaskFailedEvent;
import com.zillya.timonfech.zillwrapper.core.events.PreviewItem;
import com.zillya.timonfech.zillwrapper.core.pipeline.PipelineDispatcher;
import com.zillya.timonfech.zillwrapper.core.pipeline.OperationGraphRegistry;
import com.zillya.timonfech.zillwrapper.core.pipeline.WhiteAdminPlaceholderOrderService;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramControlMessageService;
import com.zillya.timonfech.zillwrapper.core.routing.DeliveryTargetSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingTaskExecutor {

    private final PendingTaskService pendingTaskService;
    private final InteractionBindingService interactionBindingService;
    private final UserRepository userRepository;
    private final TelegramOperationBindingRepository telegramOperationBindingRepository;
    private final OperationExecutionService operationExecutionService;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final PipelineDispatcher pipelineDispatcher;
    private final OperationGraphRegistry operationGraphRegistry;
    private final WhiteAdminPlaceholderOrderService whiteAdminPlaceholderOrderService;
    private final TelegramControlMessageService telegramControlMessageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ProductRegistry productRegistry;

    public boolean confirm(String taskId, String sourceActorId, Long chatId, Integer messageId) {
        PreparedConfirmation prepared;
        PendingTaskEntity task = pendingTaskService.get(taskId).orElse(null);
        if (task == null) {
            log.warn("Pending task confirm ignored: task missing taskId={}", taskId);
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "Preview expired or not active. Please send order again."));
            return false;
        }
        if (task.getStatus() != PendingTaskStatus.WAITING) {
            log.warn("Pending task confirm ignored: taskId={} status={}", taskId, task.getStatus());
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "Preview is not waiting for confirmation."));
            return false;
        }
        if (task.getExpiresAt() != null && Instant.now().isAfter(task.getExpiresAt())) {
            log.warn("Pending task confirm ignored: taskId={} expiredAt={}", taskId, task.getExpiresAt());
            pendingTaskService.markExpired(task, "Preview expired");
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "Preview expired. Please send order again."));
            return false;
        }
        if (task.getSourceActorId() != null && !Objects.equals(task.getSourceActorId(), sourceActorId)) {
            log.warn("Pending task confirm ignored: taskId={} sourceActor mismatch expected={} actual={}",
                    taskId,
                    task.getSourceActorId(),
                    sourceActorId);
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "This preview belongs to another user."));
            return false;
        }

        try {
            prepared = prepareConfirmed(task.getTaskId(), chatId, messageId);
            log.info("confirm_tx_done taskId={} opId={}", taskId, prepared.operationId());
        } catch (Exception e) {
            log.error("Pending task confirmation phase failed taskId={}: {}", taskId, e.getMessage(), e);
            pendingTaskService.get(taskId).ifPresent(current -> pendingTaskService.markFailed(current, e.getMessage()));
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "Failed to confirm preview: " + e.getMessage()));
            return false;
        }

        try {
            log.info("dispatch_started opId={}", prepared.operationId());
            pipelineDispatcher.dispatch(prepared.context());
            OperationExecutionEntity parent = operationExecutionService.getOperation(prepared.operationId())
                    .orElseThrow(() -> new IllegalStateException("Operation not found: " + prepared.operationId()));
            pendingTaskService.get(taskId).ifPresent(pendingTaskService::markCompleted);
            eventPublisher.publishEvent(new PendingTaskCompletedEvent(this, taskId, parent.getId()));
            return true;
        } catch (Exception e) {
            log.error("dispatch_failed opId={} reason={}", prepared.operationId(), e.getMessage(), e);
            pendingTaskService.get(taskId).ifPresent(current -> pendingTaskService.markFailed(current, e.getMessage()));
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "Failed to start order processing: " + e.getMessage()));
            return false;
        }
    }

    @Transactional
    public void cancel(String taskId) {
        pendingTaskService.markCancelled(taskId);
    }

    @Transactional
    public void applyWaPlaceholderDecision(String taskId, boolean create, Long actorUserId, Long chatId, Integer messageId) {
        PendingTaskEntity task = pendingTaskService.get(taskId).orElse(null);
        if (task == null || task.getTaskType() != PendingTaskType.ORDER_PREVIEW_CONFIRMATION) {
            return;
        }
        var binding = interactionBindingService.resolveActiveTask(taskId, chatId, messageId).orElse(null);
        if (binding == null || binding.getOperationId() == null || binding.getPreviewPayloadJson() == null) {
            return;
        }
        try {
            OrderPreviewPayload payload = objectMapper.readValue(binding.getPreviewPayloadJson(), OrderPreviewPayload.class);
            if (payload == null || !payload.isWaCreateDecisionRequired()) {
                return;
            }
            if (!create) {
                payload.setWaCreateDecisionRequired(false);
                binding.setPreviewPayloadJson(objectMapper.writeValueAsString(payload));
                telegramOperationBindingRepository.save(binding);
                interactionBindingService.bindOperationToTask(taskId, binding.getOperationId());
                runtimeRegistry.load(binding.getOperationId()).ifPresent(ctx -> {
                    ctx.setWhiteAdminId(payload.getWhiteAdminId());
                    ctx.setIncludeLegacySync(payload.getWhiteAdminId() != null);
                    runtimeRegistry.save(binding.getOperationId(), ctx);
                });
                telegramControlMessageService.refreshPreviewByTaskId(taskId);
                return;
            }

            OrderOperationContext ctx = runtimeRegistry.load(binding.getOperationId()).orElse(null);
            if (ctx == null || ctx.getUserComment() == null || ctx.getUserComment().isBlank()) {
                throw new IllegalStateException("Identifier is missing for WA placeholder create");
            }
            String fullName = resolveActorFullName(actorUserId);
            String fallbackPhone = resolveActorPhone(actorUserId);
            Long foundWaId = whiteAdminPlaceholderOrderService.createAndResolveLast3(
                    ctx.getUserComment(),
                    fullName,
                    fallbackPhone,
                    ctx.primaryEmail(),
                    ctx.getWaDocAddress(),
                    ctx.getWaComment()
            );

            ctx.setWhiteAdminId(foundWaId);
            ctx.setIncludeLegacySync(true);
            // WA placeholder flow: free-text identifier is used only for WA create request,
            // it must not be persisted as local order user comment.
            ctx.setUserComment(null);
            runtimeRegistry.save(binding.getOperationId(), ctx);

            payload.setWhiteAdminId(foundWaId);
            payload.setWaCreateDecisionRequired(false);
            payload.setUserComment(null);
            binding.setPreviewPayloadJson(objectMapper.writeValueAsString(payload));
            telegramOperationBindingRepository.save(binding);
            telegramControlMessageService.refreshPreviewByTaskId(taskId);
        } catch (Exception ex) {
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "WA placeholder create failed: " + ex.getMessage()));
        }
    }

    public void confirmWithWaPlaceholderDecision(String taskId,
                                                 boolean create,
                                                 Long actorUserId,
                                                 String sourceActorId,
                                                 Long chatId,
                                                 Integer messageId) {
        PendingTaskEntity task = pendingTaskService.get(taskId).orElse(null);
        if (task == null || task.getTaskType() != PendingTaskType.ORDER_PREVIEW_CONFIRMATION) {
            return;
        }
        var binding = interactionBindingService.resolveActiveTask(taskId, chatId, messageId).orElse(null);
        if (binding == null || binding.getOperationId() == null || binding.getPreviewPayloadJson() == null) {
            return;
        }
        try {
            OrderPreviewPayload payload = objectMapper.readValue(binding.getPreviewPayloadJson(), OrderPreviewPayload.class);
            if (payload == null || !payload.isWaCreateDecisionRequired()) {
                confirm(taskId, sourceActorId, chatId, messageId);
                return;
            }
            OrderOperationContext ctx = runtimeRegistry.load(binding.getOperationId()).orElse(null);
            if (ctx == null) {
                throw new IllegalStateException("Runtime context is missing for operation " + binding.getOperationId());
            }

            if (create) {
                if (ctx.getUserComment() == null || ctx.getUserComment().isBlank()) {
                    throw new IllegalStateException("Identifier is missing for WA placeholder create");
                }
                String fullName = resolveActorFullName(actorUserId);
                String fallbackPhone = resolveActorPhone(actorUserId);
                Long foundWaId = whiteAdminPlaceholderOrderService.createAndResolveLast3(
                        ctx.getUserComment(),
                        fullName,
                        fallbackPhone,
                        ctx.primaryEmail(),
                        ctx.getWaDocAddress(),
                        ctx.getWaComment()
                );
                ctx.setWhiteAdminId(foundWaId);
                ctx.setIncludeLegacySync(true);
                // WA placeholder flow: identifier is transport input only; do not persist as user comment.
                ctx.setUserComment(null);
                payload.setWhiteAdminId(foundWaId);
                payload.setUserComment(null);
                if (binding.getOperationId() != null) {
                    var plan = operationGraphRegistry.buildExecutionPlan(
                            com.zillya.timonfech.zillwrapper.core.entities.OperationType.LICENSE_FULFILLMENT,
                            ctx
                    );
                    ctx.replacePipelinePlan(plan.steps().stream().map(s -> s.stageType()).toList());
                    operationExecutionService.ensurePlannedStages(binding.getOperationId(), ctx, plan);
                }
            } else {
                ctx.setWhiteAdminId(null);
                ctx.setIncludeLegacySync(false);
                payload.setWhiteAdminId(null);
            }
            payload.setWaCreateDecisionRequired(false);
            runtimeRegistry.save(binding.getOperationId(), ctx);
            binding.setPreviewPayloadJson(objectMapper.writeValueAsString(payload));
            telegramOperationBindingRepository.save(binding);
        } catch (Exception ex) {
            eventPublisher.publishEvent(new PendingTaskFailedEvent(this, taskId, "WA placeholder create failed: " + ex.getMessage()));
            return;
        }
        confirm(taskId, sourceActorId, chatId, messageId);
    }

    @Transactional
    protected PreparedConfirmation prepareConfirmed(String taskId, Long chatId, Integer messageId) {
        PendingTaskEntity task = pendingTaskService.get(taskId)
                .orElseThrow(() -> new IllegalStateException("Pending task not found: " + taskId));
        pendingTaskService.markConfirmed(task);

        if (task.getTaskType() != PendingTaskType.ORDER_PREVIEW_CONFIRMATION) {
            throw new IllegalStateException("Unsupported pending task type: " + task.getTaskType());
        }
        var binding = interactionBindingService.resolveActiveTask(task.getTaskId(), chatId, messageId).orElse(null);
        if (binding == null || binding.getOperationId() == null) {
            throw new IllegalStateException("Operation binding is missing for preview task " + task.getTaskId());
        }
        if (!TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())) {
            throw new IllegalStateException("Preview is not in confirmable state: " + binding.getPreviewStatus());
        }
        OrderOperationContext context = runtimeRegistry.load(binding.getOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Runtime context snapshot is missing for operation " + binding.getOperationId()));
        context = reconcileContextWithPreview(binding, context);
        userRepository.findById(task.getInitiatorUserId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + task.getInitiatorUserId()));
        context.setOperationId(binding.getOperationId());
        interactionBindingService.bindOperationToTask(task.getTaskId(), binding.getOperationId());
        return new PreparedConfirmation(binding.getOperationId(), context);
    }

    private OrderOperationContext reconcileContextWithPreview(TelegramOperationBindingEntity binding,
                                                              OrderOperationContext base) {
        if (binding == null || binding.getPreviewPayloadJson() == null || binding.getPreviewPayloadJson().isBlank()) {
            return base;
        }
        try {
            OrderPreviewPayload payload = objectMapper.readValue(binding.getPreviewPayloadJson(), OrderPreviewPayload.class);
            if (payload == null || payload.getItems() == null || payload.getItems().isEmpty()) {
                return base;
            }
            List<OrderItemSpec> itemSpecs = new ArrayList<>();
            for (PreviewItem item : payload.getItems()) {
                ProductInfo product = productRegistry.getProductById(item.getProductId())
                        .filter(p -> p.brandId() == item.getBrandId())
                        .orElse(null);
                if (product == null) {
                    continue;
                }
                itemSpecs.add(new OrderItemSpec(
                        product,
                        item.getCount(),
                        item.getPeriod(),
                        item.getPcPerLicense(),
                        item.getOutputTypes(),
                        item.getKeyTypes(),
                        item.isSubscribed(),
                        item.getOptions()
                ));
            }
            if (itemSpecs.isEmpty()) {
                return base;
            }
            boolean hasExcel = itemSpecs.stream().anyMatch(spec -> spec.outputTypes() != null && spec.outputTypes().contains(OutputType.EXCEL));
            List<String> emails = payload.getEmails() != null && !payload.getEmails().isEmpty()
                    ? payload.getEmails()
                    : (payload.getEmail() != null && !payload.getEmail().isBlank()
                    ? List.of(payload.getEmail())
                    : (base.getEmails() != null && !base.getEmails().isEmpty() ? base.getEmails() : List.of(base.getEmail())));
            List<DeliveryTargetSpec> targets = emails.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> new DeliveryTargetSpec(
                            ContactMethodType.EMAIL,
                            v,
                            hasExcel ? OutputType.EXCEL : OutputType.TEXT
                    ))
                    .distinct()
                    .toList();
            String primaryEmail = emails.stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(base.getEmail());
            OrderOperationContext synced = new OrderOperationContext(
                    base.getSourceId(),
                    payload.getPortalId(),
                    primaryEmail,
                    itemSpecs,
                    targets,
                    base.getSourceContext()
            );
            synced.setEmails(emails);
            synced.setOperationId(base.getOperationId());
            synced.setStageExecutionId(base.getStageExecutionId());
            synced.setCurrentStage(base.getCurrentStage());
            synced.setInitiatorUserId(base.getInitiatorUserId());
            synced.setOrderId(base.getOrderId());
            synced.setPayedReady(base.isPayedReady());
            synced.setSkipDuplicateCheck(base.isSkipDuplicateCheck());
            synced.setWhiteAdminId(payload.getWhiteAdminId() != null ? payload.getWhiteAdminId() : base.getWhiteAdminId());
            synced.setUserComment(payload.getUserComment() != null ? payload.getUserComment() : base.getUserComment());
            synced.setWaDocAddress(payload.getWaDocAddress());
            synced.setWaComment(payload.getWaComment());
            synced.setPartnerOverride(base.getPartnerOverride());
            synced.setLocaleTag(payload.getLocaleTag() != null ? payload.getLocaleTag() : base.getLocaleTag());
            synced.setIncludeLegacySync((payload.getWhiteAdminId() != null ? payload.getWhiteAdminId() : base.getWhiteAdminId()) != null);
            runtimeRegistry.save(base.getOperationId(), synced);
            log.info("Confirm reconciled runtime context from preview payload: opId={} previewId={} items={} emails={}",
                    base.getOperationId(),
                    binding.getActivePreviewId(),
                    itemSpecs.size(),
                    emails.size());
            return synced;
        } catch (Exception ex) {
            log.warn("Confirm preview-context reconciliation skipped: opId={} previewId={} reason={}",
                    base.getOperationId(),
                    binding.getActivePreviewId(),
                    ex.getMessage());
            return base;
        }
    }

    private record PreparedConfirmation(BigInteger operationId, OrderOperationContext context) {}

    private String resolveActorFullName(Long actorUserId) {
        if (actorUserId == null) {
            return "";
        }
        return userRepository.findByIdWithContacts(actorUserId)
                .map(u -> u.getFullName() == null ? "" : u.getFullName().trim())
                .orElse("");
    }

    private String resolveActorPhone(Long actorUserId) {
        if (actorUserId == null) {
            return "";
        }
        return userRepository.findByIdWithContacts(actorUserId)
                .map(u -> u.getContacts() == null ? List.<String>of() : u.getContacts().stream()
                        .filter(c -> c != null && c.getType() == ContactMethodType.PHONE_NUMBER)
                        .filter(PhoneContact.class::isInstance)
                        .map(PhoneContact.class::cast)
                        .map(pc -> pc.plainValue != null ? pc.plainValue : pc.encryptedValue)
                        .filter(v -> v != null && !v.isBlank())
                        .toList())
                .flatMap(values -> values.stream().findFirst())
                .orElse("");
    }
}
