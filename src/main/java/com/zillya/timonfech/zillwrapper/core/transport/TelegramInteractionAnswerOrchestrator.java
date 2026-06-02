package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramControlMessageService;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramResolvedQuestion;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SyntheticInboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.events.QuestionRequiredEvent;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.Answer;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.StringsListAnswer;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.YesNoAnswer;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.DuplicateQuestion;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.NewStringsQuestion;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.Question;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.YesNoQuestion;
import com.zillya.timonfech.zillwrapper.core.interfaces.ResumableOperationHandler;
import com.zillya.timonfech.zillwrapper.core.pipeline.PipelineDispatcher;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramInteractionAnswerOrchestrator {
    private static final int OPERATION_ERROR_MESSAGE_MAX_LEN = 255;

    private final TelegramControlMessageService telegramControlMessageService;
    private final OperationExecutionService operationExecutionService;
    private final PipelineDispatcher pipelineDispatcher;
    private final List<ResumableOperationHandler<?, ?>> resumableHandlers;
    private final SourceRepository sourceRepository;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final ObjectMapper objectMapper;
    private final AbsSender telegramSender;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Strict correlation:
     * - text answer is accepted only when it is a reply to the specific question message
     * - callback answer is accepted only from the specific question message
     */
    public boolean canHandle(TelegramInboundEvent event) {
        if (event.getPayload().hasCallbackQuery()) {
            String data = event.getPayload().getCallbackQuery().getData();
            if (data == null || !data.startsWith("qa_")) {
                return false;
            }
            CallbackQuery callback = event.getPayload().getCallbackQuery();
            Long chatId = callback.getMessage() != null ? callback.getMessage().getChatId() : null;
            Integer messageId = callback.getMessage() != null ? callback.getMessage().getMessageId() : null;
            if (chatId == null || messageId == null) {
                return false;
            }
            String questionId = extractQuestionId(data);
            if (questionId != null) {
                if (telegramControlMessageService.findWaitingQuestionById(questionId).isPresent()) {
                    return true;
                }
            }
            return telegramControlMessageService.findWaitingQuestionByMessage(chatId, messageId).isPresent();
        }
        if (!event.getPayload().hasMessage()) {
            return false;
        }
        Message message = event.getPayload().getMessage();
        if (message.getReplyToMessage() == null || message.getReplyToMessage().getMessageId() == null) {
            return false;
        }
        return telegramControlMessageService.findWaitingQuestionByReply(
                message.getChatId(),
                message.getReplyToMessage().getMessageId()
        ).isPresent();
    }

    public boolean tryHandle(TelegramInboundEvent event, UserEntity authenticatedUser) {
        if (event.getPayload().hasCallbackQuery()) {
            return handleCallback(event.getPayload().getCallbackQuery(), authenticatedUser);
        }
        if (!event.getPayload().hasMessage()) {
            return false;
        }
        Message message = event.getPayload().getMessage();
        if (message.getReplyToMessage() == null || message.getReplyToMessage().getMessageId() == null) {
            return false;
        }
        return handleReply(message, authenticatedUser);
    }

    private boolean handleReply(Message message, UserEntity authenticatedUser) {
        Long chatId = message.getChatId();
        Integer replyToMessageId = message.getReplyToMessage().getMessageId();
        Optional<TelegramResolvedQuestion> resolvedOpt = telegramControlMessageService
                .findWaitingQuestionByReply(chatId, replyToMessageId);
        if (resolvedOpt.isEmpty()) {
            return false;
        }

        TelegramResolvedQuestion resolved = resolvedOpt.get();
        Optional<OperationExecutionEntity> stageOpt = operationExecutionService.getOperation(resolved.question().getStageExecutionId());
        if (stageOpt.isEmpty()) {
            return true;
        }
        OperationExecutionEntity stageExecution = stageOpt.get();
        if (!isStageReadyForAnswer(stageExecution, resolved.operationId(), authenticatedUser)) {
            sendHint(chatId, "This question is no longer active.");
            return true;
        }

        Question<?> question = readQuestion(resolved.question().getQuestionType(), resolved.question().getQuestionPayloadJson()).orElse(null);
        if (question == null) {
            sendHint(chatId, "Question payload is invalid. Please contact support.");
            return true;
        }

        if (question instanceof YesNoQuestion || question instanceof DuplicateQuestion) {
            sendHint(chatId, "Please use the Yes/No buttons in the question message.");
            return true;
        }

        Answer answer = parseReplyAnswer(message, question);
        if (answer == null) {
            sendHint(chatId, "Please reply with a non-empty value.");
            return true;
        }

        return resumeQuestion(chatId, resolved, stageExecution, question, answer);
    }

    private boolean handleCallback(CallbackQuery callbackQuery, UserEntity authenticatedUser) {
        String data = callbackQuery.getData();
        if (data == null || !data.startsWith("qa_")) {
            return false;
        }
        answerCallback(callbackQuery.getId());
        String questionId = extractQuestionId(data);
        Optional<TelegramResolvedQuestion> resolvedOpt;
        Long chatId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : null;
        Integer messageId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getMessageId() : null;
        if (questionId != null) {
            resolvedOpt = telegramControlMessageService.findWaitingQuestionById(questionId);
            if (resolvedOpt.isEmpty() && messageId != null && chatId != null) {
                resolvedOpt = telegramControlMessageService.findWaitingQuestionByMessage(chatId, messageId);
            }
        } else if (messageId != null && chatId != null) {
            resolvedOpt = telegramControlMessageService.findWaitingQuestionByMessage(chatId, messageId);
        } else {
            return true;
        }
        if (resolvedOpt.isEmpty()) {
            return true;
        }

        TelegramResolvedQuestion resolved = resolvedOpt.get();
        Optional<OperationExecutionEntity> stageOpt = operationExecutionService.getOperation(resolved.question().getStageExecutionId());
        if (stageOpt.isEmpty()) {
            return true;
        }
        OperationExecutionEntity stageExecution = stageOpt.get();
        if (!isStageReadyForAnswer(stageExecution, resolved.operationId(), authenticatedUser)) {
            sendHint(chatId, "This question is no longer active.");
            return true;
        }

        Question<?> question = readQuestion(resolved.question().getQuestionType(), resolved.question().getQuestionPayloadJson()).orElse(null);
        if (question == null) {
            sendHint(chatId, "Question payload is invalid. Please contact support.");
            return true;
        }

        Answer answer = parseCallbackAnswer(data, question);
        if (answer == null) {
            sendHint(chatId, "Unsupported callback answer.");
            return true;
        }

        if (chatId != null && messageId != null) {
            telegramControlMessageService.closeQuestionMessage(chatId, messageId);
        }
        return resumeQuestion(chatId, resolved, stageExecution, question, answer);
    }

    private boolean resumeQuestion(Long chatId,
                                   TelegramResolvedQuestion resolved,
                                   OperationExecutionEntity stageExecution,
                                   Question<?> question,
                                   Answer answer) {
        boolean parentBlocking = !stageExecution.isCancelable();
        String answerPayload = writeJson(answer);
        OperationResult<?> result;
        try {
            result = callResume(stageExecution, question, answer);
        } catch (NeedUserInteractionException ex) {
            telegramControlMessageService.markQuestionSuperseded(
                    resolved.operationId(),
                    resolved.question().getQuestionId(),
                    answerPayload
            );
            if (parentBlocking) {
                operationExecutionService.markParentWaiting(resolved.operationId());
            }
            eventPublisher.publishEvent(new QuestionRequiredEvent(
                    this,
                    resolved.operationId(),
                    resolved.question().getStageExecutionId(),
                    parentBlocking,
                    ex.getQuestion()
            ));
            return true;
        }

        if (result == null) {
            telegramControlMessageService.markQuestionFailed(resolved.operationId(), resolved.question().getQuestionId(), answerPayload);
            sendHint(chatId, "No resumable handler found for this question.");
            return true;
        }

        if (!result.isSuccess()) {
            telegramControlMessageService.markQuestionFailed(resolved.operationId(), resolved.question().getQuestionId(), answerPayload);
            stageExecution.setStatus(OperationStatus.FAILED);
            stageExecution.setErrorMessage(truncateForErrorMessage(result.getErrorMessage()));
            stageExecution.setCompletedAt(Instant.now());
            operationExecutionService.save(stageExecution);
            eventPublisher.publishEvent(new HandlingInfoEvent(this, stageExecution));
            operationExecutionService.markParentFailed(resolved.operationId(), result.getErrorMessage());
            sendHint(chatId, result.getErrorMessage() != null ? result.getErrorMessage() : "Unable to process answer.");
            return true;
        }

        telegramControlMessageService.markQuestionAnswered(resolved.operationId(), resolved.question().getQuestionId(), answerPayload);

        if (stageExecution.getStatus() == OperationStatus.WAITING_INTERACTION
                || stageExecution.getStatus() == OperationStatus.RUNNING) {
            stageExecution.setStatus(OperationStatus.DONE);
            stageExecution.setCompletedAt(Instant.now());
            operationExecutionService.save(stageExecution);
            eventPublisher.publishEvent(new HandlingInfoEvent(this, stageExecution));
        }
        runtimeRegistry.patchAfterStageResult(
                resolved.operationId(),
                stageExecution.getEntityId(),
                null,
                question instanceof DuplicateQuestion && answer instanceof YesNoAnswer yesNoAnswer && yesNoAnswer.confirmed()
                        ? Boolean.TRUE
                        : null
        );

        operationExecutionService.unblockParentIfNoCrucialWaiting(resolved.operationId());
        operationExecutionService.getRootOperation(resolved.operationId())
                .ifPresent(parent -> eventPublisher.publishEvent(new HandlingInfoEvent(this, parent)));
        continueParentPipeline(resolved.operationId(), stageExecution);
        return true;
    }

    private boolean isStageReadyForAnswer(OperationExecutionEntity stageExecution,
                                          BigInteger expectedParentId,
                                          UserEntity authenticatedUser) {
        if (stageExecution.getParentId() == null || !expectedParentId.equals(stageExecution.getParentId())) {
            return false;
        }
        if (stageExecution.getStatus() != OperationStatus.WAITING_INTERACTION
                && stageExecution.getStatus() != OperationStatus.RUNNING) {
            return false;
        }
        if (stageExecution.getInitiatorUserId() != null
                && authenticatedUser != null
                && !stageExecution.getInitiatorUserId().equals(authenticatedUser.getId())) {
            return false;
        }
        return true;
    }

    private Optional<Question<?>> readQuestion(String questionType, String json) {
        try {
            if ("YesNoQuestion".equals(questionType)) {
                return Optional.of(objectMapper.readValue(json, YesNoQuestion.class));
            }
            if ("NewStringsQuestion".equals(questionType)) {
                return Optional.of(objectMapper.readValue(json, NewStringsQuestion.class));
            }
            if ("DuplicateQuestion".equals(questionType)) {
                return Optional.of(objectMapper.readValue(json, DuplicateQuestion.class));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse question {}: {}", questionType, e.getMessage());
            return Optional.empty();
        }
    }

    private Answer parseReplyAnswer(Message message, Question<?> question) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (question instanceof NewStringsQuestion) {
            return new StringsListAnswer(List.of(text.trim()));
        }
        return null;
    }

    private Answer parseCallbackAnswer(String callbackData, Question<?> question) {
        if (!(question instanceof YesNoQuestion || question instanceof DuplicateQuestion)) {
            return null;
        }
        String action = callbackData;
        int idx = callbackData.indexOf(':');
        if (idx > 0) {
            action = callbackData.substring(0, idx);
        }
        return switch (action) {
            case "qa_yes" -> new YesNoAnswer(true);
            case "qa_no" -> new YesNoAnswer(false);
            default -> null;
        };
    }

    private String extractQuestionId(String callbackData) {
        int idx = callbackData.indexOf(':');
        if (idx < 0 || idx + 1 >= callbackData.length()) {
            return null;
        }
        String id = callbackData.substring(idx + 1).trim();
        return id.isBlank() ? null : id;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OperationResult<?> callResume(OperationExecutionEntity stageExecution, Question<?> question, Answer answer) {
        for (ResumableOperationHandler handler : resumableHandlers) {
            try {
                if (handler.supports(stageExecution, question, answer)) {
                    return handler.resume(stageExecution, question, answer);
                }
            } catch (NeedUserInteractionException ex) {
                throw ex;
            } catch (ClassCastException ignored) {
                // handler expects another question/answer subtype
            } catch (Exception ex) {
                log.error("Resume handler failed for stage {}: {}", stageExecution.getId(), ex.getMessage(), ex);
                return OperationResult.fail(ex.getMessage(), false);
            }
        }
        return null;
    }

    private void continueParentPipeline(BigInteger parentOperationId, OperationExecutionEntity stageExecution) {
        OperationExecutionEntity parent = operationExecutionService.getRootOperation(parentOperationId).orElse(null);
        if (parent == null) {
            return;
        }

        Long orderId = resolveOrderId(parentOperationId, stageExecution);
        if (orderId == null) {
            log.warn("Cannot continue parent pipeline {}: orderId not resolved from stage {}", parentOperationId, stageExecution.getId());
            return;
        }
        OrderOperationContext context = runtimeRegistry.load(parentOperationId).orElseGet(() -> {
            SourceEntity sourceEntity = sourceRepository.findById(parent.getSourceId()).orElse(null);
            if (sourceEntity == null) {
                return null;
            }
            OrderOperationContext fallback = new OrderOperationContext(
                    parent.getSourceId(),
                    orderId,
                    null,
                    List.of(),
                    List.of(),
                    new SyntheticInboundEvent(sourceEntity)
            );
            fallback.setInitiatorUserId(parent.getInitiatorUserId());
            fallback.setCurrentStage(parent.getOperationType());
            return fallback;
        });
        if (context == null) {
            return;
        }
        context.setOperationId(parentOperationId);
        context.setOrderId(orderId);
        context.setInitiatorUserId(parent.getInitiatorUserId());
        context.setCurrentStage(parent.getOperationType());
        runtimeRegistry.patchAfterStageResult(parentOperationId, orderId, parent.getOperationType(), null);
        pipelineDispatcher.dispatch(context);
    }

    private Long resolveOrderId(BigInteger parentOperationId, OperationExecutionEntity stageExecution) {
        if (stageExecution.getEntityId() != null) {
            return stageExecution.getEntityId();
        }
        return operationExecutionService.getChildren(parentOperationId).stream()
                .map(OperationExecutionEntity::getEntityId)
                .filter(id -> id != null && id > 0)
                .findFirst()
                .orElse(null);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void sendHint(Long chatId, String text) {
        if (chatId == null) {
            log.warn("Skip hint because chatId is null: {}", text);
            return;
        }
        try {
            telegramSender.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send hint to chat {}: {}", chatId, e.getMessage());
        }
    }

    private void answerCallback(String callbackId) {
        if (callbackId == null) {
            return;
        }
        try {
            telegramSender.execute(new AnswerCallbackQuery(callbackId));
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback {}: {}", callbackId, e.getMessage());
        }
    }

    private String truncateForErrorMessage(String text) {
        if (text == null || text.length() <= OPERATION_ERROR_MESSAGE_MAX_LEN) {
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
