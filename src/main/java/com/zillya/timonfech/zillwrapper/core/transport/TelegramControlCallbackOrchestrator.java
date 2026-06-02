package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.communication.InteractionBindingService;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.events.PendingTaskDecisionRequestedEvent;
import com.zillya.timonfech.zillwrapper.core.security.OperationAuthorizationService;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramControlCallbackOrchestrator {
    private final OperationExecutionService operationService;
    private final InteractionBindingService interactionBindingService;
    private final TelegramSearchActionOrchestrator telegramSearchActionOrchestrator;
    private final ApplicationEventPublisher eventPublisher;
    private final AbsSender telegramSender;
    private final OperationAuthorizationService operationAuthorizationService;
    private final EnrichmentTaskManager enrichmentTaskManager;

    public boolean canHandle(TelegramInboundEvent event) {
        if (event.getPayload() == null || !event.getPayload().hasCallbackQuery()) {
            return false;
        }
        String data = event.getPayload().getCallbackQuery().getData();
        return data != null && (data.startsWith("op_")
                || data.startsWith("search_")
                || data.startsWith("task_")
                || data.startsWith("preview_"));
    }

    public boolean tryHandle(TelegramInboundEvent event, UserEntity user) {
        CallbackQuery query = event.getPayload().getCallbackQuery();
        if (query == null) {
            return false;
        }
        String data = query.getData();
        if (data == null) {
            return false;
        }
        if (data.startsWith("op_")) {
            return handleOperationControl(query, user);
        }
        if (data.startsWith("search_")) {
            return telegramSearchActionOrchestrator.handleSearchCallback(query, user);
        }
        if (data.startsWith("task_") || data.startsWith("preview_")) {
            return handlePendingTask(query, user);
        }
        return false;
    }

    private boolean handleOperationControl(CallbackQuery query, UserEntity actor) {
        String[] parts = query.getData().split(":");
        if (parts.length < 2) {
            answerCallback(query.getId(), "Invalid control callback.");
            return true;
        }
        String action = parts[0];
        BigInteger opId = new BigInteger(parts[1]);
        if (!operationAuthorizationService.canControlOperation(actor, opId, action)) {
            log.warn("Operation control denied action={} opId={} actorUserId={} chatId={}",
                    action,
                    opId,
                    actor != null ? actor.getId() : null,
                    query.getMessage() != null ? query.getMessage().getChatId() : null);
            answerCallback(query.getId(), "Access denied.");
            return true;
        }
        switch (action) {
            case "op_cancel" -> {
                operationService.cancel(opId);
                enrichmentTaskManager.cancelByOperationId(opId);
            }
            case "op_resume" -> operationService.resume(opId);
            default -> {
                answerCallback(query.getId(), "Unsupported operation action.");
                return true;
            }
        }
        answerCallback(query.getId(), null);
        return true;
    }

    private boolean handlePendingTask(CallbackQuery query, UserEntity user) {
        String[] parts = query.getData().split(":");
        if (parts.length != 2 || query.getMessage() == null || query.getFrom() == null) {
            answerCallback(query.getId(), "Invalid preview callback.");
            return true;
        }
        String action = parts[0];
        if ("preview_confirm".equals(action)) {
            action = "task_confirm";
        } else if ("preview_cancel".equals(action)) {
            action = "task_cancel";
        }

        String taskId = parts[1];
        Long chatId = query.getMessage().getChatId();
        Integer messageId = query.getMessage().getMessageId();
        Long actorUserId = user != null ? user.getId() : null;
        String sourceActorId = query.getFrom().getId().toString();
        Optional<TelegramOperationBindingEntity> bindingOpt = interactionBindingService.resolveActiveTask(taskId, chatId, messageId);
        if (bindingOpt.isEmpty()) {
            log.warn("Task callback ignored: inactive task action={} taskId={} chatId={} messageId={}",
                    action, taskId, chatId, messageId);
            answerCallback(query.getId(), "Preview expired or not active. Please send order again.");
            return true;
        }

        if ("task_confirm".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.CONFIRM,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        } else if ("task_cancel".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.CANCEL,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        } else if ("task_wa_yes".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_YES,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        } else if ("task_wa_no".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_NO,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        } else if ("task_wa_create_confirm".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.WA_CREATE_AND_CONFIRM,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        } else if ("task_wa_skip_confirm".equals(action)) {
            eventPublisher.publishEvent(new PendingTaskDecisionRequestedEvent(
                    this,
                    taskId,
                    PendingTaskDecisionRequestedEvent.Decision.WA_SKIP_AND_CONFIRM,
                    actorUserId,
                    sourceActorId,
                    chatId,
                    messageId,
                    Instant.now()
            ));
        }
        answerCallback(query.getId(), null);
        return true;
    }

    private void answerCallback(String callbackId, String text) {
        if (callbackId == null) {
            return;
        }
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackId);
            if (text != null && !text.isBlank()) {
                answer.setText(text);
                answer.setShowAlert(false);
            }
            telegramSender.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }
}
