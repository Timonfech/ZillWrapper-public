package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.communication.finalization.FinalNotificationContext;
import com.zillya.timonfech.zillwrapper.core.communication.finalization.FinalNotificationPolicy;
import com.zillya.timonfech.zillwrapper.core.communication.sections.ControlMessageComposer;
import com.zillya.timonfech.zillwrapper.core.communication.sections.ControlMessageContext;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.events.*;
import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.DuplicateQuestion;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.YesNoQuestion;
import com.zillya.timonfech.zillwrapper.core.repos.*;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * Maintains control message per parent operation and manages question queue state in telegram binding row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramControlMessageService {

    private final AbsSender telegramSender;
    private final OperationExecutionService operationService;
    private final TelegramOperationBindingRepository bindingRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final LicenseRepository licenseRepository;
    private final LicenseSubscriptionRepository licenseSubscriptionRepository;
    private final List<FinalNotificationPolicy> finalNotificationPolicies;
    private final TelegramBindingUpdateService bindingUpdateService;
    private final ControlMessageComposer controlMessageComposer;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    @Qualifier("pipelineTaskExecutor")
    private final Executor pipelineTaskExecutor;
    @Value("${telegram.question.delete-on-answer:false}")
    private boolean deleteQuestionOnAnswer;
    @Value("${telegram.control.handling-info.debounce-ms:200}")
    private long handlingInfoDebounceMs;
    @Value("${whiteAdminPanel.target}")
    private String whiteAdminBaseUrl;
    @Value("${whiteAdminPanel.orders.detailsPage}")
    private String whiteAdminOrderDetailsPage;

    private final Map<Long, Instant> lastUpdateTimestamps = new ConcurrentHashMap<>();
    private final Map<BigInteger, Integer> initialReplyToMessageByOperationId = new ConcurrentHashMap<>();
    private final ConcurrentMap<BigInteger, Instant> lateStageWarningNotified = new ConcurrentHashMap<>();
    private final ConcurrentMap<BigInteger, Long> handlingInfoLastSeenMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<BigInteger, Boolean> handlingInfoDrainRunning = new ConcurrentHashMap<>();
    private final ConcurrentMap<BigInteger, String> enrichmentLastRenderedText = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60000)
    public void expirePreviews() {
        Instant now = Instant.now();
        for (TelegramOperationBindingEntity binding : bindingRepository.findAll()) {
            if (!TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())
                    && !TelegramPreviewStatus.PARSE_ERROR.name().equals(binding.getPreviewStatus())) {
                continue;
            }
            if (binding.getPreviewExpiresAt() != null && now.isAfter(binding.getPreviewExpiresAt())) {
                markPreviewExpiredAndDisableOrDeleteMessage(binding);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOperationCreated(OperationCreatedEvent event) {
        if (!(event.getSourceContext() instanceof com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent tgEvent)) {
            return;
        }
        ensureBindingForTelegramOperation(event.getOperationId(), tgEvent);
    }

    public void ensureBindingForTelegramOperation(BigInteger operationId,
                                                  com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent tgEvent) {
        if (operationId == null || tgEvent == null || tgEvent.getPayload() == null) {
            return;
        }
        if (bindingRepository.findByOperationId(operationId).isPresent()) {
            return;
        }
        Long chatId = null;
        Integer sourceMessageId = null;
        String localeTag = null;
        if (tgEvent.getPayload().getMessage() != null) {
            chatId = tgEvent.getPayload().getMessage().getChatId();
            sourceMessageId = tgEvent.getPayload().getMessage().getMessageId();
            localeTag = tgEvent.getPayload().getMessage().getFrom() != null
                    ? tgEvent.getPayload().getMessage().getFrom().getLanguageCode()
                    : null;
        } else if (tgEvent.getPayload().getCallbackQuery() != null
                && tgEvent.getPayload().getCallbackQuery().getMessage() != null) {
            chatId = tgEvent.getPayload().getCallbackQuery().getMessage().getChatId();
            sourceMessageId = tgEvent.getPayload().getCallbackQuery().getMessage().getMessageId();
            localeTag = tgEvent.getPayload().getCallbackQuery().getFrom() != null
                    ? tgEvent.getPayload().getCallbackQuery().getFrom().getLanguageCode()
                    : null;
        }
        if (chatId == null) {
            return;
        }
        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(operationId);
        binding.setChatId(chatId);
        binding.setQuestionQueueJson("[]");
        binding.setInteractionDeliveryStatus("NOT_SENT");
        binding.setLocaleTag(normalizeLocaleTag(localeTag));
        bindingRepository.save(binding);
        if (sourceMessageId != null) {
            initialReplyToMessageByOperationId.put(operationId, sourceMessageId);
        }
        log.info("Telegram binding created for operationId={} chatId={} locale={}",
                operationId,
                chatId,
                localeTag);
        operationService.getOperation(operationId).ifPresent(this::updateControlMessage);
    }

    @EventListener
    public void onHandlingInfo(HandlingInfoEvent event) {
        OperationExecutionEntity execution = event.getExecution();
        if (execution == null || !execution.isInteractionEnabled()) {
            return;
        }
        BigInteger rootId = rootOperationId(execution);
        long now = System.currentTimeMillis();
        handlingInfoLastSeenMs.put(rootId, now);
        log.debug("handling_info_enqueued rootOpId={} stageOpId={}", rootId, execution.getId());
        if (handlingInfoDrainRunning.putIfAbsent(rootId, Boolean.TRUE) != null) {
            log.debug("handling_info_coalesced rootOpId={}", rootId);
            return;
        }
        pipelineTaskExecutor.execute(() -> drainHandlingInfo(rootId));
    }

    public void refreshControlMessage(BigInteger operationId) {
        if (operationId == null) {
            return;
        }
        operationService.getOperation(operationId).ifPresent(this::updateControlMessage);
    }

    @EventListener
    public void onOperationCompleted(OperationCompletedEvent event) {
        if (event.getRootOperation() == null) {
            return;
        }
        pipelineTaskExecutor.execute(() -> tryHandleFinalSuccess(event.getRootOperation()));
    }

    @EventListener
    public void onQuestionRequired(QuestionRequiredEvent event) {
        try {
            Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByOperationId(event.getParentOperationId());
            if (bindingOpt.isEmpty()) {
                log.warn("QuestionRequired delivery skipped: binding missing for parentOpId={} stageExecId={}",
                        event.getParentOperationId(),
                        event.getStageExecutionId());
                return;
            }
            TelegramOperationBindingEntity binding = bindingOpt.get();
            publishQuestionMessage(binding, event);
            operationService.getOperation(event.getParentOperationId()).ifPresent(this::updateControlMessage);
        } catch (Throwable ex) {
            log.error("QuestionRequired listener failed for parentOpId={} stageExecId={}: {}",
                    event.getParentOperationId(),
                    event.getStageExecutionId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @EventListener
    public void onInboundError(InboundProcessingErrorEvent event) {
        if (event.getChatId() == null || event.getSafeMessage() == null || event.getSafeMessage().isBlank()) {
            return;
        }
        try {
            telegramSender.execute(SendMessage.builder()
                    .chatId(event.getChatId().toString())
                    .text(event.getSafeMessage())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send inbound error to chat {}: {}", event.getChatId(), e.getMessage());
        }
    }

    @EventListener
    public void onPendingTaskCompleted(PendingTaskCompletedEvent event) {
        try {
            bindingRepository.findByActivePreviewId(event.getTaskId()).ifPresent(binding -> {
                if (binding.getOperationId() == null) {
                    binding.setOperationId(event.getOperationId());
                }
                binding.setPreviewStatus(TelegramPreviewStatus.CONFIRMED.name());
                binding.setInteractionDeliveryStatus("NOT_SENT");
                if (binding.getControlMessageId() == null) {
                    binding.setControlMessageId(binding.getPreviewMessageId());
                }
                bindingUpdateService.applyByOperationId(binding.getOperationId(), "pending_task_completed", existing -> {
                    if (existing.getOperationId() == null) {
                        existing.setOperationId(event.getOperationId());
                    }
                    existing.setPreviewStatus(TelegramPreviewStatus.CONFIRMED.name());
                    existing.setInteractionDeliveryStatus("NOT_SENT");
                    if (existing.getControlMessageId() == null) {
                        existing.setControlMessageId(existing.getPreviewMessageId());
                    }
                    return true;
                });
                // Avoid duplicate Telegram edit: when preview message is reused as control message,
                // final policy clears keyboard itself.
                if (!Objects.equals(binding.getPreviewMessageId(), binding.getControlMessageId())) {
                    clearPreviewKeyboard(binding);
                }
                operationService.getOperation(event.getOperationId()).ifPresent(this::updateControlMessage);
            });
        } catch (Exception ex) {
            log.error("PendingTaskCompleted listener failed taskId={} opId={}: {}",
                    event.getTaskId(),
                    event.getOperationId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @EventListener
    public void onPendingTaskFailed(PendingTaskFailedEvent event) {
        try {
            bindingRepository.findByActivePreviewId(event.getTaskId()).ifPresent(binding -> {
                if (binding.getChatId() == null || event.getSafeMessage() == null || event.getSafeMessage().isBlank()) {
                    return;
                }
                try {
                    telegramSender.execute(SendMessage.builder()
                            .chatId(binding.getChatId().toString())
                            .text(event.getSafeMessage())
                            .build());
                } catch (TelegramApiException e) {
                    log.warn("Failed to send pending task failure chat={} taskId={}: {}",
                            binding.getChatId(),
                            event.getTaskId(),
                            e.getMessage());
                }
            });
        } catch (Exception ex) {
            log.error("PendingTaskFailed listener failed taskId={}: {}", event.getTaskId(), ex.getMessage(), ex);
        }
    }

    @EventListener
    public void onPendingTaskDecisionRequested(PendingTaskDecisionRequestedEvent event) {
        if (event.getDecision() != PendingTaskDecisionRequestedEvent.Decision.CANCEL) {
            return;
        }
        try {
            Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository
                    .findByChatIdAndActivePreviewId(event.getChatId(), event.getTaskId());
            if (bindingOpt.isEmpty()) {
                return;
            }
            TelegramOperationBindingEntity binding = bindingOpt.get();
            if (binding.getPreviewMessageId() != null
                    && event.getMessageId() != null
                    && !binding.getPreviewMessageId().equals(event.getMessageId())) {
                return;
            }
            markPreviewCancelledAndDeleteMessage(binding);
        } catch (Exception ex) {
            log.warn("Failed to process pending task cancel UI update taskId={} chatId={}: {}",
                    event.getTaskId(),
                    event.getChatId(),
                    ex.getMessage());
        }
    }

    public void applyLateStageWarningToFinalMessage(BigInteger parentOperationId,
                                                    BigInteger stageExecutionId,
                                                    OperationType stageType,
                                                    OperationStatus status,
                                                    String summary) {
        if (parentOperationId == null || stageExecutionId == null || stageType == null || status == null) {
            return;
        }
        if (summary == null || summary.isBlank()) {
            return;
        }
        if (status != OperationStatus.FAILED && status != OperationStatus.PARTIALLY_DONE) {
            return;
        }
        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByOperationId(parentOperationId);
        if (bindingOpt.isEmpty()) {
            return;
        }
        TelegramOperationBindingEntity binding = bindingOpt.get();
        if (binding.getControlMessageId() == null || binding.getChatId() == null) {
            return;
        }
        OperationExecutionEntity root = operationService.getRootOperation(parentOperationId).orElse(null);
        if (root == null || root.getStatus() != OperationStatus.DONE || binding.getFinalNotifiedAt() == null) {
            return;
        }
        if (lateStageWarningNotified.putIfAbsent(stageExecutionId, Instant.now()) != null) {
            log.info("late_warning_dedup_hit stageExecId={} parentOpId={}", stageExecutionId, parentOperationId);
            return;
        }
        String prefix = status == OperationStatus.PARTIALLY_DONE ? "[WARNING] " : "[ERROR] ";
        String warningLine = prefix + stageType + ": " + summary;
        forceEditControlMessageWithWarning(root, binding, warningLine);
    }

    @EventListener
    public void onOrderPreviewRequested(OrderPreviewRequestedEvent event) {
        String text = buildPreviewText(event);
        InlineKeyboardMarkup keyboard = buildPreviewKeyboard(event.getPreviewId(), event.getPayload());

        Integer messageId = null;
        Integer replyToMessageId = event.getSourceEvent() != null
                && event.getSourceEvent().getPayload() != null
                && event.getSourceEvent().getPayload().getMessage() != null
                ? event.getSourceEvent().getPayload().getMessage().getMessageId()
                : null;
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(event.getChatId().toString())
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(keyboard);
            if (replyToMessageId != null) {
                builder.replyToMessageId(replyToMessageId).allowSendingWithoutReply(true);
            }
            Message sent = telegramSender.execute(builder.build());
            messageId = sent.getMessageId();
            log.info("Preview keyboard sent: chatId={}, messageId={}, previewId={}, buttons=2",
                    event.getChatId(), messageId, event.getPreviewId());
        } catch (TelegramApiException e) {
            log.warn("Failed to send preview message for chat {}: {}", event.getChatId(), e.getMessage());
        }

        upsertPreviewBinding(event, messageId);
    }

    public Optional<TelegramResolvedQuestion> findWaitingQuestionByReply(Long chatId, Integer replyToMessageId) {
        if (replyToMessageId == null) {
            return Optional.empty();
        }
        return findWaitingQuestion(chatId, replyToMessageId);
    }

    public Optional<TelegramResolvedQuestion> findWaitingQuestionByMessage(Long chatId, Integer messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return findWaitingQuestion(chatId, messageId);
    }

    public Optional<TelegramResolvedQuestion> findWaitingQuestionById(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return Optional.empty();
        }
        List<TelegramResolvedQuestion> matches = new ArrayList<>();
        for (TelegramOperationBindingEntity binding : bindingRepository.findAll()) {
            List<TelegramQuestionQueueItem> queue = readQueue(binding.getQuestionQueueJson());
            for (TelegramQuestionQueueItem item : queue) {
                if (item.getStatus() == TelegramQuestionStatus.WAITING
                        && questionId.equals(item.getQuestionId())) {
                    matches.add(new TelegramResolvedQuestion(binding.getOperationId(), item));
                }
            }
        }
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public void markQuestionAnswered(BigInteger operationId, String questionId, String answerPayloadJson) {
        mutateQueue(operationId, queue -> updateQuestionStatus(
                queue,
                questionId,
                TelegramQuestionStatus.ANSWERED,
                answerPayloadJson
        ));
    }

    public void markQuestionFailed(BigInteger operationId, String questionId, String answerPayloadJson) {
        mutateQueue(operationId, queue -> updateQuestionStatus(
                queue,
                questionId,
                TelegramQuestionStatus.FAILED,
                answerPayloadJson
        ));
    }

    public void markQuestionSuperseded(BigInteger operationId, String questionId, String answerPayloadJson) {
        mutateQueue(operationId, queue -> updateQuestionStatus(
                queue,
                questionId,
                TelegramQuestionStatus.SUPERSEDED,
                answerPayloadJson
        ));
    }

    public void clearQuestionKeyboard(Long chatId, Integer messageId) {
        try {
            telegramSender.execute(EditMessageReplyMarkup.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .replyMarkup(null)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to clear question keyboard chat={}, message={}: {}", chatId, messageId, e.getMessage());
        }
    }

    public void closeQuestionMessage(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }
        if (deleteQuestionOnAnswer) {
            try {
                telegramSender.execute(DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
                return;
            } catch (TelegramApiException e) {
                log.warn("Failed to delete question message chat={}, message={}: {}. Falling back to clear keyboard.",
                        chatId, messageId, e.getMessage());
            }
        }
        clearQuestionKeyboard(chatId, messageId);
    }

    public synchronized void notifySuccess(BigInteger operationId,
                                           List<com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact> artifacts,
                                           List<Long> successItemIds) {
        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByOperationId(operationId);
        if (bindingOpt.isEmpty()) {
            log.warn("Cannot notify success for op {}: binding missing", operationId);
            return;
        }

        TelegramOperationBindingEntity binding = bindingOpt.get();
        Long chatId = binding.getChatId();

        StringBuilder sb = new StringBuilder();
        java.util.Locale locale = localeOf(binding);
        sb.append(msg("telegram.operation.completed", locale)).append("\n\n");
        sb.append(msg("telegram.operation.processed_items", locale)).append(" ").append(successItemIds.size()).append("\n");
        sb.append(msg("telegram.operation.generated_artifacts", locale)).append(" ").append(artifacts.size()).append("\n");

        if (binding.getControlMessageId() != null) {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(binding.getControlMessageId())
                    .text(sb.toString())
                    .parseMode("HTML")
                    .build();
            try {
                telegramSender.execute(edit);
            } catch (TelegramApiException e) {
                log.error("Failed to update final control message: {}", e.getMessage());
            }
        } else {
            try {
                telegramSender.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(sb.toString())
                        .parseMode("HTML")
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send final control message: {}", e.getMessage());
            }
        }

        for (com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact artifact : artifacts) {
            SendDocument sendDoc = SendDocument.builder()
                    .chatId(chatId.toString())
                    .document(new InputFile(new ByteArrayInputStream(artifact.getContent()), artifact.getFilename()))
                    .caption("Artifact: " + artifact.getFilename())
                    .build();
            try {
                telegramSender.execute(sendDoc);
            } catch (TelegramApiException e) {
                log.error("Failed to send artifact {}: {}", artifact.getFilename(), e.getMessage());
            }
        }
    }

    public Optional<TelegramOperationBindingEntity> resolveActivePreview(Long chatId, Integer messageId, String previewId) {
        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByChatIdAndActivePreviewId(chatId, previewId);
        if (bindingOpt.isEmpty()) {
            return Optional.empty();
        }
        TelegramOperationBindingEntity binding = bindingOpt.get();
        if (binding.getPreviewMessageId() == null || !binding.getPreviewMessageId().equals(messageId)) {
            return Optional.empty();
        }
        if (!TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())) {
            return Optional.empty();
        }
        if (binding.getPreviewExpiresAt() != null && Instant.now().isAfter(binding.getPreviewExpiresAt())) {
            markPreviewExpiredAndDisableOrDeleteMessage(binding);
            return Optional.empty();
        }
        return Optional.of(binding);
    }

    public void markPreviewCancelledAndDeleteMessage(TelegramOperationBindingEntity binding) {
        bindingUpdateService.applyByOperationId(binding.getOperationId(), "preview_cancelled", existing -> {
            if (TelegramPreviewStatus.CANCELLED.name().equals(existing.getPreviewStatus())) {
                return false;
            }
            existing.setPreviewStatus(TelegramPreviewStatus.CANCELLED.name());
            return true;
        });
        deletePreviewMessageOrFallbackCancelled(binding);
    }

    public void markPreviewExpiredAndDisableOrDeleteMessage(TelegramOperationBindingEntity binding) {
        bindingUpdateService.applyByOperationId(binding.getOperationId(), "preview_expired", existing -> {
            if (TelegramPreviewStatus.EXPIRED.name().equals(existing.getPreviewStatus())) {
                return false;
            }
            existing.setPreviewStatus(TelegramPreviewStatus.EXPIRED.name());
            return true;
        });
        deletePreviewMessageOrFallbackCancelled(binding);
    }

    private synchronized void updateControlMessage(OperationExecutionEntity execution) {
        BigInteger rootId = rootOperationId(execution);
        OperationExecutionEntity rootExecution = operationService.getOperation(rootId).orElse(execution);
        OperationExecutionEntity viewExecution = shouldPreferRootState(execution, rootExecution)
                ? rootExecution
                : execution;
        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByOperationId(rootId);
        if (bindingOpt.isEmpty()) {
            return;
        }

        TelegramOperationBindingEntity binding = bindingOpt.get();
        if (binding.getFinalNotifiedAt() != null && rootExecution.getStatus() == OperationStatus.DONE) {
            // Final policy already wrote terminal success state to control message.
            // Ignore late stage lifecycle updates to avoid overwriting final text.
            return;
        }
        Long chatId = binding.getChatId();
        Instant now = Instant.now();

        java.util.Locale locale = localeOf(binding);
        String text = buildStatusText(viewExecution, rootId, binding, locale);
        InlineKeyboardMarkup keyboard = buildControlKeyboard(viewExecution, rootId, locale);
        if (rootExecution.getOperationType() == OperationType.ENTITY_ENRICHMENT
                && binding.getControlMessageId() != null
                && text.equals(enrichmentLastRenderedText.get(rootId))) {
            log.debug("enrichment_control_skip_unchanged rootOpId={}", rootId);
            return;
        }

        if (binding.getControlMessageId() == null) {
            SendMessage.SendMessageBuilder sendBuilder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(keyboard)
                    .parseMode("HTML");
            Integer replyToMessageId = initialReplyToMessageByOperationId.get(rootId);
            if (replyToMessageId != null) {
                sendBuilder.replyToMessageId(replyToMessageId).allowSendingWithoutReply(true);
            }
            SendMessage send = sendBuilder.build();
            try {
                Message msg = telegramSender.execute(send);
                bindingUpdateService.applyByOperationId(rootId, "control_message_created", existing -> {
                    if (existing.getControlMessageId() != null && existing.getControlMessageId().equals(msg.getMessageId())) {
                        return false;
                    }
                    existing.setControlMessageId(msg.getMessageId());
                    return true;
                });
                initialReplyToMessageByOperationId.remove(rootId);
                if (rootExecution.getOperationType() == OperationType.ENTITY_ENRICHMENT) {
                    enrichmentLastRenderedText.put(rootId, text);
                }
                log.info("Control message created: opId={}, chatId={}, messageId={}, hasKeyboard={}",
                        rootId, chatId, msg.getMessageId(), keyboard != null);
            } catch (TelegramApiException e) {
                log.error("Failed to send control message to chat {}: {}", chatId, e.getMessage());
            }
        } else {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(binding.getControlMessageId())
                    .text(text)
                    .replyMarkup(keyboard)
                    .parseMode("HTML")
                    .build();
            try {
                telegramSender.execute(edit);
                if (rootExecution.getOperationType() == OperationType.ENTITY_ENRICHMENT) {
                    enrichmentLastRenderedText.put(rootId, text);
                }
                log.info("Control message updated: opId={}, chatId={}, messageId={}, hasKeyboard={}",
                        rootId, chatId, binding.getControlMessageId(), keyboard != null);
            } catch (TelegramApiException e) {
                if (!e.getMessage().contains("message is not modified")) {
                    log.error("Failed to edit control message in chat {}: {}", chatId, e.getMessage());
                }
            }
        }

        lastUpdateTimestamps.put(chatId, now);
        if (rootExecution.getOperationType() == OperationType.ENTITY_ENRICHMENT
                && (rootExecution.getStatus() == OperationStatus.DONE
                || rootExecution.getStatus() == OperationStatus.CANCELLED
                || rootExecution.getStatus() == OperationStatus.FAILED
                || rootExecution.getStatus() == OperationStatus.PARTIALLY_DONE)) {
            enrichmentLastRenderedText.remove(rootId);
        }
    }

    private void drainHandlingInfo(BigInteger rootId) {
        try {
            while (true) {
                Long snapshot = handlingInfoLastSeenMs.get(rootId);
                if (snapshot == null) {
                    return;
                }
                sleepDebounceWindow();
                Long current = handlingInfoLastSeenMs.get(rootId);
                if (current == null) {
                    return;
                }
                if (!current.equals(snapshot)) {
                    log.debug("handling_info_coalesced rootOpId={} reason=newer_event", rootId);
                    continue;
                }
                Long stableTs = current;
                operationService.getOperation(rootId)
                        .filter(OperationExecutionEntity::isInteractionEnabled)
                        .ifPresent(execution -> {
                            updateControlMessage(execution);
                            log.debug("handling_info_rendered rootOpId={}", rootId);
                        });
                handlingInfoLastSeenMs.remove(rootId, stableTs);
                return;
            }
        } finally {
            handlingInfoDrainRunning.remove(rootId);
            Long current = handlingInfoLastSeenMs.get(rootId);
            if (current != null && handlingInfoDrainRunning.putIfAbsent(rootId, Boolean.TRUE) == null) {
                pipelineTaskExecutor.execute(() -> drainHandlingInfo(rootId));
            }
        }
    }

    private void sleepDebounceWindow() {
        long waitMs = handlingInfoDebounceMs <= 0 ? 0 : handlingInfoDebounceMs;
        if (waitMs == 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void forceEditControlMessageWithWarning(OperationExecutionEntity rootExecution,
                                                    TelegramOperationBindingEntity binding,
                                                    String warningLine) {
        java.util.Locale locale = localeOf(binding);
        String base = buildStatusText(rootExecution, rootExecution.getId(), binding, locale);
        String text = base + "\n\n" + warningLine;
        InlineKeyboardMarkup keyboard = buildControlKeyboard(rootExecution, rootExecution.getId(), locale);
        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(binding.getChatId().toString())
                    .messageId(binding.getControlMessageId())
                    .text(text)
                    .replyMarkup(keyboard)
                    .parseMode("HTML")
                    .build());
            log.info("final_edit_applied opId={} stageWarning='{}'", rootExecution.getId(), warningLine);
        } catch (TelegramApiException e) {
            if (!e.getMessage().contains("message is not modified")) {
                log.warn("Failed to edit final message with warning opId={} stageWarning='{}': {}",
                        rootExecution.getId(),
                        warningLine,
                        e.getMessage());
            }
        }
    }

    private boolean shouldPreferRootState(OperationExecutionEntity incoming, OperationExecutionEntity rootExecution) {
        if (incoming.getParentId() == null) {
            return false;
        }
        return rootExecution.getStatus() == OperationStatus.DONE
                || rootExecution.getStatus() == OperationStatus.PARTIALLY_DONE
                || rootExecution.getStatus() == OperationStatus.FAILED
                || rootExecution.getStatus() == OperationStatus.CANCELLED;
    }

    private void tryHandleFinalSuccess(OperationExecutionEntity execution) {
        if (execution.getStatus() != OperationStatus.DONE) {
            log.debug("Final success skip: execution {} status={} is not DONE", execution.getId(), execution.getStatus());
            return;
        }

        BigInteger rootId = rootOperationId(execution);
        enrichmentLastRenderedText.remove(rootId);
        OperationExecutionEntity rootExecution = execution;
        if (execution.getParentId() != null) {
            rootExecution = operationService.getOperation(rootId).orElse(execution);
        }
        if (rootExecution.getStatus() != OperationStatus.DONE) {
            log.debug("Final success skip: root operation {} status={} is not DONE", rootId, rootExecution.getStatus());
            return;
        }

        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByOperationId(rootId);
        if (bindingOpt.isEmpty()) {
            log.warn("Final success skip: binding not found for root operation {}", rootId);
            return;
        }
        TelegramOperationBindingEntity binding = bindingOpt.get();
        if (binding.getFinalNotifiedAt() != null) {
            log.debug("Final success skip: already notified root operation {} at {}", rootId, binding.getFinalNotifiedAt());
            return;
        }

        FinalNotificationContext context = buildFinalNotificationContext(rootExecution, binding);
        if (context == null) {
            log.warn("Final success skip: context is null for root operation {}", rootId);
            return;
        }

        FinalNotificationPolicy policy = finalNotificationPolicies.stream()
                .filter(p -> p.supports(context))
                .findFirst()
                .orElse(null);
        if (policy == null) {
            log.warn("Final success skip: no policy supports root operation {} type={}",
                    rootId,
                    rootExecution.getOperationType());
            return;
        }
        log.info("Final success policy selected root operation {} policy={} orderId={} newOrderCreated={}",
                rootId,
                policy.kind(),
                context.orderId(),
                context.newOrderCreated());

        policy.notify(context);
        bindingUpdateService.applyByOperationId(rootId, "final_success_store", existing -> {
            if (existing.getFinalNotifiedAt() != null) {
                return false;
            }
            existing.setFinalNotificationKind(policy.kind());
            existing.setFinalNotifiedAt(Instant.now());
            return true;
        });
        log.info("Final success notification stored root operation {} kind={}", rootId, policy.kind());
    }

    private FinalNotificationContext buildFinalNotificationContext(OperationExecutionEntity parentExecution,
                                                                   TelegramOperationBindingEntity binding) {
        List<OperationExecutionEntity> children = operationService.getChildren(parentExecution.getId());

        Optional<OperationExecutionEntity> orderCreationChild = children.stream()
                .filter(child -> child.getOperationType() == OperationType.ORDER_CREATION)
                .filter(child -> child.getStatus() == OperationStatus.DONE)
                .filter(child -> child.getEntityId() != null)
                .findFirst();

        boolean newOrderCreated = parentExecution.getOperationType() == OperationType.LICENSE_FULFILLMENT
                && orderCreationChild.isPresent();
        Long orderId = orderCreationChild.map(OperationExecutionEntity::getEntityId).orElse(parentExecution.getEntityId());

        OrderEntity order = null;
        List<OrderItemEntity> items = List.of();
        List<LicenseEntity> licenses = List.of();
        List<String> stageWarnings = children.stream()
                .filter(child -> child.getErrorMessage() != null && !child.getErrorMessage().isBlank())
                .filter(child -> (child.getStatus() == OperationStatus.PARTIALLY_DONE
                        || child.getStatus() == OperationStatus.FAILED)
                        || (child.getOperationType() == OperationType.MODIFY_STATUS
                        && child.getStatus() == OperationStatus.DONE
                        && child.getErrorMessage().startsWith("STATUS_RESULT ")))
                .map(child -> child.getOperationType() == OperationType.MODIFY_STATUS
                        && child.getErrorMessage().startsWith("STATUS_RESULT ")
                        ? child.getErrorMessage()
                        : child.getOperationType() + ": " + child.getErrorMessage())
                .toList();
        List<String> nonCriticalWarnings = List.of();
        if (orderId != null) {
            order = orderRepository.findByIdWithDeliveryTargets(orderId).orElse(null);
            items = orderItemRepository.findByOrderId(orderId);
            licenses = licenseRepository.findByOrderId(orderId);
            nonCriticalWarnings = licenseSubscriptionRepository.findByOrderId(orderId).stream()
                    .filter(sub -> sub.getStatus() == LicenseSubscriptionEntity.SubscriptionStatus.ERROR)
                    .map(sub -> "Subscription setup warning for license "
                            + sub.getLicenseId()
                            + (sub.getLastError() == null ? "" : ": " + sub.getLastError()))
                    .toList();
        }

        return new FinalNotificationContext(
                parentExecution,
                binding,
                newOrderCreated,
                orderId,
                order,
                items,
                licenses,
                stageWarnings,
                nonCriticalWarnings,
                localeOf(binding)
        );
    }

    private void publishQuestionMessage(TelegramOperationBindingEntity binding, QuestionRequiredEvent event) {
        java.util.Locale locale = localeOf(binding);
        String questionId = UUID.randomUUID().toString();
        String questionText = buildQuestionTextNormalized(event.getQuestion(), event.getStageExecutionId(), locale);
        InlineKeyboardMarkup keyboard = buildQuestionKeyboard(event.getQuestion(), locale, questionId);

        Message sent = null;
        try {
            sent = telegramSender.execute(SendMessage.builder()
                    .chatId(binding.getChatId().toString())
                    .text(questionText)
                    .replyMarkup(keyboard)
                    .parseMode("HTML")
                    .build());
            log.info("Question message sent: parentOpId={}, stageExecId={}, chatId={}, messageId={}, hasKeyboard={}",
                    event.getParentOperationId(),
                    event.getStageExecutionId(),
                    binding.getChatId(),
                    sent.getMessageId(),
                    keyboard != null);
            binding.setInteractionDeliveryStatus("SENT");
        } catch (TelegramApiException e) {
            log.error("Failed to send question message for operation {}: {}", binding.getOperationId(), e.getMessage());
            binding.setInteractionDeliveryStatus("FAILED");
        }
        bindingUpdateService.applyByOperationId(event.getParentOperationId(), "question_delivery_status", existing -> {
            String newStatus = binding.getInteractionDeliveryStatus();
            if (newStatus != null && newStatus.equals(existing.getInteractionDeliveryStatus())) {
                return false;
            }
            existing.setInteractionDeliveryStatus(newStatus);
            return true;
        });

        TelegramQuestionQueueItem item = new TelegramQuestionQueueItem();
        item.setQuestionId(questionId);
        item.setParentOperationId(event.getParentOperationId());
        item.setStageExecutionId(event.getStageExecutionId());
        item.setQuestionType(event.getQuestion().getClass().getSimpleName());
        item.setQuestionPayloadJson(writeQuestionPayload(event.getQuestion()));
        item.setQuestionMessageId(sent != null ? sent.getMessageId() : null);
        item.setStatus(TelegramQuestionStatus.WAITING);
        item.setCreatedAtEpochMs(Instant.now().toEpochMilli());
        mutateQueue(binding.getOperationId(), queue -> {
            for (TelegramQuestionQueueItem queued : queue) {
                if (queued.getStatus() == TelegramQuestionStatus.WAITING
                        && event.getStageExecutionId().equals(queued.getStageExecutionId())) {
                    queued.setStatus(TelegramQuestionStatus.SUPERSEDED);
                    queued.setAnsweredAtEpochMs(Instant.now().toEpochMilli());
                }
            }
            queue.add(item);
            return true;
        });
    }

    private Optional<TelegramResolvedQuestion> findWaitingQuestion(Long chatId, Integer messageId) {
        List<TelegramResolvedQuestion> matches = new ArrayList<>();

        for (TelegramOperationBindingEntity binding : bindingRepository.findByChatId(chatId)) {
            List<TelegramQuestionQueueItem> queue = readQueue(binding.getQuestionQueueJson());
            for (TelegramQuestionQueueItem item : queue) {
                if (item.getStatus() == TelegramQuestionStatus.WAITING
                        && item.getQuestionMessageId() != null
                        && item.getQuestionMessageId().equals(messageId)) {
                    matches.add(new TelegramResolvedQuestion(binding.getOperationId(), item));
                }
            }
        }

        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    private String buildStatusText(OperationExecutionEntity execution,
                                   BigInteger rootId,
                                   TelegramOperationBindingEntity binding,
                                   java.util.Locale locale) {
        OperationExecutionEntity rootExecution = operationService.getOperation(rootId).orElse(execution);
        ControlMessageContext context = new ControlMessageContext(
                rootId,
                execution,
                rootExecution,
                operationService.getChildren(rootId),
                locale,
                messageSource
        );
        String text = controlMessageComposer.compose(context);
        OperationStatus statusForError = execution.getStatus();
        boolean shouldShowError = statusForError == OperationStatus.FAILED
                || statusForError == OperationStatus.PARTIALLY_DONE;
        if (shouldShowError && execution.getErrorMessage() != null && !execution.getErrorMessage().isBlank()) {
            text = text + "\n\n" + msg("telegram.control.error", locale) + " " + execution.getErrorMessage();
        }
        return text;
    }

    private InlineKeyboardMarkup buildControlKeyboard(OperationExecutionEntity execution, BigInteger rootId, java.util.Locale locale) {
        if (execution.getStatus() == OperationStatus.DONE
                || execution.getStatus() == OperationStatus.PARTIALLY_DONE
                || execution.getStatus() == OperationStatus.FAILED
                || execution.getStatus() == OperationStatus.CANCELLED) {
            return null;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (execution.isCancelable()) {
            row.add(InlineKeyboardButton.builder()
                    .text(msg("telegram.button.cancel", locale))
                    .callbackData("op_cancel:" + rootId)
                    .build());
        }

        rows.add(row);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildQuestionKeyboard(Question<?> question, java.util.Locale locale, String questionId) {
        if (!(question instanceof YesNoQuestion) && !(question instanceof DuplicateQuestion)) {
            return null;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                InlineKeyboardButton.builder().text(msg("telegram.button.yes", locale)).callbackData("qa_yes:" + questionId).build(),
                InlineKeyboardButton.builder().text(msg("telegram.button.no", locale)).callbackData("qa_no:" + questionId).build()
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private String buildQuestionTextNormalized(Question<?> question, BigInteger stageExecutionId, java.util.Locale locale) {
        StringBuilder sb = new StringBuilder();
        sb.append("[QUESTION] <b>").append(msg("telegram.question.action_required", locale)).append("</b>\n");
        sb.append(msg("telegram.question.stage_execution", locale)).append(" ").append(stageExecutionId).append("\n\n");

        if (question instanceof YesNoQuestion yesNoQuestion) {
            sb.append(yesNoQuestion.message());
            return sb.toString();
        }
        if (question instanceof DuplicateQuestion duplicateQuestion) {
            sb.append(msg("telegram.question.duplicate_detected", locale)).append("\n");
            sb.append(msg("telegram.question.current", locale)).append(" ").append(formatCurrentDuplicateReference(duplicateQuestion)).append("\n");
            sb.append(msg("telegram.question.existing", locale)).append(" ").append(duplicateQuestion.duplicateEntityType()).append(" #").append(duplicateQuestion.duplicateEntityId()).append("\n\n");
            sb.append(msg("telegram.question.send_again", locale));
            return sb.toString();
        }
        if (question instanceof com.zillya.timonfech.zillwrapper.core.interactions.questions.NewStringsQuestion stringsQuestion) {
            sb.append("Please provide corrected values:\n");
            stringsQuestion.dataKeyValue().forEach((k, v) -> {
                if ("orderId".equals(k)) {
                    return;
                }
                sb.append("- ").append(k).append(": ").append(v).append("\n");
            });
            sb.append("\nReply to this message with the corrected value.");
            return sb.toString();
        }
        sb.append("Reply to this message with the required input.");
        return sb.toString();
    }

    private String buildQuestionText(Question<?> question, BigInteger stageExecutionId, java.util.Locale locale) {
        StringBuilder sb = new StringBuilder();
        sb.append("❓ <b>").append(msg("telegram.question.action_required", locale)).append("</b>\n");
        sb.append(msg("telegram.question.stage_execution", locale)).append(" ").append(stageExecutionId).append("\n\n");

        if (question instanceof YesNoQuestion yesNoQuestion) {
            sb.append(yesNoQuestion.message());
            return sb.toString();
        }
        if (question instanceof DuplicateQuestion duplicateQuestion) {
            sb.append(msg("telegram.question.duplicate_detected", locale)).append("\n");
            sb.append(msg("telegram.question.current", locale)).append(" ").append(formatCurrentDuplicateReference(duplicateQuestion)).append("\n");
            sb.append(msg("telegram.question.existing", locale)).append(" ").append(duplicateQuestion.duplicateEntityType()).append(" #").append(duplicateQuestion.duplicateEntityId()).append("\n\n");
            sb.append(msg("telegram.question.send_again", locale));
            return sb.toString();
        }
        if (question instanceof com.zillya.timonfech.zillwrapper.core.interactions.questions.NewStringsQuestion stringsQuestion) {
            sb.append("Please provide corrected values:\n");
            stringsQuestion.dataKeyValue().forEach((k, v) -> {
                if ("orderId".equals(k)) {
                    return;
                }
                sb.append("• ").append(k).append(": ").append(v).append("\n");
            });
            sb.append("\nReply to this message with the corrected value.");
            return sb.toString();
        }
        sb.append("Reply to this message with the required input.");
        return sb.toString();
    }

    private String buildPreviewText(OrderPreviewRequestedEvent event) {
        StringBuilder sb = new StringBuilder();
        OrderPreviewPayload payload = event.getPayload();
        java.util.Locale locale = localeFromTag(payload != null ? payload.getLocaleTag() : null);
        sb.append("<b>").append(msg("telegram.preview.title", locale)).append("</b>").append("\n");
        appendIfPresent(sb, msg("telegram.preview.portal", locale), payload.getPortalId(), true);
        appendWhiteAdmin(sb, payload.getWhiteAdminId());
        appendIfPresent(sb, msg("telegram.preview.comment", locale), payload.getUserComment(), true);
        appendIfPresent(sb, msg("telegram.preview.wa.address", locale), payload.getWaDocAddress(), true);
        appendIfPresent(sb, msg("telegram.preview.wa.comment", locale), payload.getWaComment(), true);
        appendIfPresent(sb, msg("telegram.preview.email", locale), resolvePreviewEmails(payload), true);
        if (payload.isWaCreateDecisionRequired() && payload.getWhiteAdminId() == null && payload.getUserComment() != null && !payload.getUserComment().isBlank()) {
            sb.append("\n")
                    .append("<b>").append(msg("telegram.preview.wa.question.title", locale)).append("</b>").append("\n")
                    .append(msg("telegram.preview.wa.question.body", locale))
                    .append("\n");
        }
        if (payload.isLocaleExplicit()) {
            appendIfPresent(sb, msg("telegram.preview.locale", locale), payload.getLocaleTag(), true);
        }
        if (payload.getItems() != null && !payload.getItems().isEmpty()) {
            sb.append("<b>").append(msg("telegram.preview.items", locale)).append(":</b> ").append(payload.getItems().size()).append("\n");
        }
        int subscribedCount = 0;
        int totalItems = 0;
        int idx = 1;
        for (PreviewItem item : payload.getItems() == null ? List.<PreviewItem>of() : payload.getItems()) {
            totalItems++;
            if (item.isSubscribed()) {
                subscribedCount++;
            }
            sb.append(idx++).append(". <b>")
                    .append(item.getProductName());
            sb.append("</b>");
            if (item.getCount() > 0) {
                sb.append(" x").append(item.getCount());
            }
            if (item.getPcPerLicense() > 0) {
                sb.append(", pc=").append(item.getPcPerLicense());
            }
            if (item.getPeriod() != null) {
                sb.append(", period=").append(item.getPeriod().amount()).append(" ").append(item.getPeriod().unit());
            }
            if (item.getKeyTypes() != null && !item.getKeyTypes().isEmpty()) {
                sb.append(", ").append(msg("telegram.preview.keytype", locale)).append("=").append(item.getKeyTypes());
            }
            if (item.getOutputTypes() != null && !item.getOutputTypes().isEmpty()) {
                sb.append(", ").append(msg("telegram.preview.output", locale)).append("=").append(item.getOutputTypes());
            }
            sb.append(", ").append(msg("telegram.preview.subscribe", locale)).append("=")
                    .append(item.isSubscribed() ? msg("telegram.preview.on", locale) : msg("telegram.preview.off", locale));
            if (item.getOptions() != null && item.getOptions().serverNumber() != null) {
                sb.append(", server=").append(item.getOptions().serverNumber());
            }
            sb.append("\n");
        }
        if (totalItems > 0) {
            sb.append(msg("telegram.preview.subscription", locale)).append(": ")
                    .append(subscribedCount).append("/").append(totalItems).append("\n");
        }
        sb.append("\n").append("<i>").append(msg("telegram.preview.confirm_within", locale)).append("</i>");
        return sb.toString();
    }

    public void refreshPreviewByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        bindingRepository.findByActivePreviewId(taskId).ifPresent(binding -> {
            if (binding.getChatId() == null || binding.getPreviewMessageId() == null || binding.getPreviewPayloadJson() == null) {
                return;
            }
            try {
                OrderPreviewPayload payload = objectMapper.readValue(binding.getPreviewPayloadJson(), OrderPreviewPayload.class);
                OrderPreviewRequestedEvent pseudo = new OrderPreviewRequestedEvent(
                        this,
                        binding.getOperationId(),
                        binding.getChatId(),
                        binding.getSourceMessageId(),
                        null,
                        taskId,
                        payload,
                        Instant.now(),
                        binding.getPreviewExpiresAt(),
                        null
                );
                telegramSender.execute(EditMessageText.builder()
                        .chatId(binding.getChatId().toString())
                        .messageId(binding.getPreviewMessageId())
                        .text(buildPreviewText(pseudo))
                        .parseMode("HTML")
                        .replyMarkup(buildPreviewKeyboard(taskId, payload))
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to refresh preview by taskId={} reason={}", taskId, ex.getMessage());
            }
        });
    }

    private InlineKeyboardMarkup buildPreviewKeyboard(String previewId, OrderPreviewPayload payload) {
        java.util.Locale locale = localeFromTag(payload != null ? payload.getLocaleTag() : null);
        if (payload != null && payload.isWaCreateDecisionRequired() && payload.getWhiteAdminId() == null) {
            return InlineKeyboardMarkup.builder()
                    .keyboard(List.of(
                            List.of(
                                    InlineKeyboardButton.builder().text(msg("telegram.preview.button.create_continue", locale)).callbackData("task_wa_create_confirm:" + previewId).build()
                            ),
                            List.of(
                                    InlineKeyboardButton.builder().text(msg("telegram.preview.button.skip_continue", locale)).callbackData("task_wa_skip_confirm:" + previewId).build()
                            ),
                            List.of(
                                    InlineKeyboardButton.builder().text(msg("telegram.button.cancel", locale)).callbackData("task_cancel:" + previewId).build()
                            )
                    ))
                    .build();
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(
                        InlineKeyboardButton.builder().text(msg("telegram.button.confirm", locale)).callbackData("task_confirm:" + previewId).build(),
                        InlineKeyboardButton.builder().text(msg("telegram.button.cancel", locale)).callbackData("task_cancel:" + previewId).build()
                )))
                .build();
    }

    private void appendWhiteAdmin(StringBuilder sb, Long whiteAdminId) {
        if (whiteAdminId == null) {
            return;
        }
        String url = buildWhiteAdminOrderUrl(whiteAdminId);
        if (url == null) {
            sb.append("• WhiteAdmin: ").append("<code>").append(whiteAdminId).append("</code>").append("\n");
            return;
        }
        sb.append("• WhiteAdmin: ").append("<a href=\"").append(url).append("\"><code>").append(whiteAdminId).append("</code></a>").append("\n");
    }

    private String resolvePreviewEmails(OrderPreviewPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload.getEmails() != null && !payload.getEmails().isEmpty()) {
            return String.join(", ", payload.getEmails());
        }
        return payload.getEmail();
    }

    private String buildWhiteAdminOrderUrl(Long whiteAdminId) {
        if (whiteAdminId == null || whiteAdminBaseUrl == null || whiteAdminOrderDetailsPage == null) {
            return null;
        }
        try {
            java.net.URI base = java.net.URI.create(whiteAdminBaseUrl);
            String path = whiteAdminOrderDetailsPage.startsWith("/") ? whiteAdminOrderDetailsPage.substring(1) : whiteAdminOrderDetailsPage;
            java.net.URI resolved = base.resolve(path);
            String separator = resolved.toString().contains("?") ? "&" : "?";
            return resolved + separator + "id=" + whiteAdminId;
        } catch (Exception ex) {
            return null;
        }
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value, boolean bullet) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        if (bullet) {
            sb.append("• ");
        }
        sb.append(label).append(": ").append("<code>").append(value).append("</code>").append("\n");
    }

    private java.util.Locale localeFromTag(String tag) {
        return java.util.Locale.forLanguageTag(normalizeLocaleTag(tag));
    }

    private void upsertPreviewBinding(OrderPreviewRequestedEvent event, Integer previewMessageId) {
        TelegramOperationBindingEntity binding = bindingRepository.findByChatIdAndActivePreviewId(event.getChatId(), event.getPreviewId())
                .orElseGet(TelegramOperationBindingEntity::new);
        binding.setOperationId(event.getOperationId());
        binding.setQuestionQueueJson(binding.getQuestionQueueJson() == null ? "[]" : binding.getQuestionQueueJson());
        binding.setChatId(event.getChatId());
        binding.setActivePreviewId(event.getPreviewId());
        binding.setPreviewMessageId(previewMessageId);
        binding.setSourceMessageId(event.getSourceMessageId());
        binding.setSourceMessageHash(event.getPayload() != null ? event.getPayload().getRawTextHash() : null);
        binding.setPreviewPayloadJson(writePreviewPayload(event.getPayload()));
        binding.setPreviewCreatedAt(event.getCreatedAt());
        binding.setPreviewExpiresAt(event.getExpiresAt());
        binding.setPreviewStatus(TelegramPreviewStatus.WAITING.name());
        binding.setInteractionDeliveryStatus("NOT_SENT");
        if (binding.getId() == null) {
            bindingRepository.save(binding);
            return;
        }
        bindingUpdateService.applyByOperationId(event.getOperationId(), "preview_upsert", existing -> {
            existing.setQuestionQueueJson(existing.getQuestionQueueJson() == null ? "[]" : existing.getQuestionQueueJson());
            existing.setActivePreviewId(event.getPreviewId());
            existing.setPreviewMessageId(previewMessageId);
            existing.setSourceMessageId(event.getSourceMessageId());
            existing.setSourceMessageHash(event.getPayload() != null ? event.getPayload().getRawTextHash() : null);
            existing.setPreviewPayloadJson(writePreviewPayload(event.getPayload()));
            existing.setPreviewCreatedAt(event.getCreatedAt());
            existing.setPreviewExpiresAt(event.getExpiresAt());
            existing.setPreviewStatus(TelegramPreviewStatus.WAITING.name());
            existing.setInteractionDeliveryStatus("NOT_SENT");
            return true;
        });
    }

    private void deletePreviewMessageOrFallbackCancelled(TelegramOperationBindingEntity binding) {
        if (binding.getPreviewMessageId() == null || binding.getChatId() == null) {
            return;
        }
        try {
            telegramSender.execute(DeleteMessage.builder()
                    .chatId(binding.getChatId().toString())
                    .messageId(binding.getPreviewMessageId())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete preview message chat={} msg={}: {}. Fallback to cancelled text.",
                    binding.getChatId(),
                    binding.getPreviewMessageId(),
                    e.getMessage());
            try {
                telegramSender.execute(EditMessageText.builder()
                        .chatId(binding.getChatId().toString())
                        .messageId(binding.getPreviewMessageId())
                        .text("Cancelled")
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException inner) {
                log.warn("Failed to set cancelled text chat={} msg={}: {}",
                        binding.getChatId(),
                        binding.getPreviewMessageId(),
                        inner.getMessage());
            }
        }
    }

    private void clearPreviewKeyboard(TelegramOperationBindingEntity binding) {
        if (binding.getPreviewMessageId() == null || binding.getChatId() == null) {
            return;
        }
        try {
            telegramSender.execute(EditMessageReplyMarkup.builder()
                    .chatId(binding.getChatId().toString())
                    .messageId(binding.getPreviewMessageId())
                    .replyMarkup(null)
                    .build());
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("Preview keyboard already cleared chat={} msg={}", binding.getChatId(), binding.getPreviewMessageId());
                return;
            }
            log.warn("Failed to clear preview keyboard chat={} msg={}: {}", binding.getChatId(), binding.getPreviewMessageId(), e.getMessage());
        }
    }

    private String writePreviewPayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String msg(String code, java.util.Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private java.util.Locale localeOf(TelegramOperationBindingEntity binding) {
        return java.util.Locale.forLanguageTag(normalizeLocaleTag(binding.getLocaleTag()));
    }

    private String normalizeLocaleTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "uk";
        }
        String normalized = tag.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ru")) {
            return "uk";
        }
        return normalized;
    }

    private BigInteger rootOperationId(OperationExecutionEntity execution) {
        return execution.getParentId() != null ? execution.getParentId() : execution.getId();
    }

    private String formatCurrentDuplicateReference(DuplicateQuestion question) {
        if (question.currentReference() != null && !question.currentReference().isBlank()) {
            return question.currentReference();
        }
        if (question.entityId() != null) {
            return question.entityType() + " #" + question.entityId();
        }
        return question.entityType();
    }

    private String writeQuestionPayload(Question<?> question) {
        try {
            return objectMapper.writeValueAsString(question);
        } catch (Exception e) {
            log.warn("Failed to serialize question payload: {}", e.getMessage());
            return "{}";
        }
    }

    private List<TelegramQuestionQueueItem> readQueue(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            TelegramQuestionQueueItem[] array = objectMapper.readValue(json, TelegramQuestionQueueItem[].class);
            List<TelegramQuestionQueueItem> result = new ArrayList<>();
            if (array != null) {
                for (TelegramQuestionQueueItem item : array) {
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse question queue json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeQueue(List<TelegramQuestionQueueItem> queue) {
        try {
            return objectMapper.writeValueAsString(queue);
        } catch (Exception e) {
            log.warn("Failed to serialize question queue: {}", e.getMessage());
            return "[]";
        }
    }

    private boolean updateQuestionStatus(List<TelegramQuestionQueueItem> queue,
                                         String questionId,
                                         TelegramQuestionStatus targetStatus,
                                         String answerPayloadJson) {
        for (TelegramQuestionQueueItem item : queue) {
            if (questionId.equals(item.getQuestionId()) && item.getStatus() == TelegramQuestionStatus.WAITING) {
                item.setStatus(targetStatus);
                item.setAnsweredAtEpochMs(Instant.now().toEpochMilli());
                item.setAnswerPayloadJson(answerPayloadJson);
                return true;
            }
        }
        return false;
    }

    private void mutateQueue(BigInteger operationId, QueueMutation mutation) {
        bindingUpdateService.applyByOperationId(operationId, "queue_mutation", binding -> {
            List<TelegramQuestionQueueItem> queue = readQueue(binding.getQuestionQueueJson());
            boolean changed = mutation.apply(queue);
            if (!changed) {
                return false;
            }
            trimQueue(queue);
            binding.setQuestionQueueJson(writeQueue(queue));
            return true;
        });
    }

    @FunctionalInterface
    private interface QueueMutation {
        boolean apply(List<TelegramQuestionQueueItem> queue);
    }

    private void trimQueue(List<TelegramQuestionQueueItem> queue) {
        final int keepAnswered = 20;
        List<TelegramQuestionQueueItem> waiting = queue.stream()
                .filter(item -> item.getStatus() == TelegramQuestionStatus.WAITING)
                .toList();
        List<TelegramQuestionQueueItem> closed = queue.stream()
                .filter(item -> item.getStatus() != TelegramQuestionStatus.WAITING)
                .sorted((a, b) -> {
                    Long ai = a.getAnsweredAtEpochMs() != null ? a.getAnsweredAtEpochMs() : a.getCreatedAtEpochMs();
                    Long bi = b.getAnsweredAtEpochMs() != null ? b.getAnsweredAtEpochMs() : b.getCreatedAtEpochMs();
                    if (ai == null && bi == null) return 0;
                    if (ai == null) return 1;
                    if (bi == null) return -1;
                    return Long.compare(bi, ai);
                })
                .limit(keepAnswered)
                .toList();

        queue.clear();
        queue.addAll(waiting);
        queue.addAll(closed);
    }
}
