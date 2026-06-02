package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramControlMessageService;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramQuestionQueueItem;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramQuestionStatus;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramResolvedQuestion;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.events.QuestionRequiredEvent;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.YesNoQuestion;
import com.zillya.timonfech.zillwrapper.core.interfaces.ResumableOperationHandler;
import com.zillya.timonfech.zillwrapper.core.pipeline.PipelineDispatcher;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TelegramInteractionAnswerOrchestratorTest {

    @Test
    void shouldIgnoreFreeTextWithoutReply() {
        TelegramControlMessageService controlService = Mockito.mock(TelegramControlMessageService.class);
        OperationExecutionService operationService = Mockito.mock(OperationExecutionService.class);
        PipelineDispatcher dispatcher = Mockito.mock(PipelineDispatcher.class);
        SourceRepository sourceRepository = Mockito.mock(SourceRepository.class);
        OperationRuntimeRegistry runtimeRegistry = Mockito.mock(OperationRuntimeRegistry.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        AbsSender telegramSender = Mockito.mock(AbsSender.class);

        TelegramInteractionAnswerOrchestrator orchestrator = new TelegramInteractionAnswerOrchestrator(
                controlService,
                operationService,
                dispatcher,
                List.of(),
                sourceRepository,
                runtimeRegistry,
                JsonMapper.builder().build(),
                telegramSender,
                eventPublisher
        );

        Update update = Mockito.mock(Update.class);
        Message message = Mockito.mock(Message.class);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getReplyToMessage()).thenReturn(null);

        TelegramInboundEvent event = Mockito.mock(TelegramInboundEvent.class);
        when(event.getPayload()).thenReturn(update);

        UserEntity user = new UserEntity();
        user.setId(7L);

        boolean handled = orchestrator.tryHandle(event, user);

        assertFalse(handled);
        verify(controlService, never()).findWaitingQuestionByReply(any(), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void shouldSupersedeQuestionAndPublishNewOneWhenResumeNeedsInteraction() {
        TelegramControlMessageService controlService = Mockito.mock(TelegramControlMessageService.class);
        OperationExecutionService operationService = Mockito.mock(OperationExecutionService.class);
        PipelineDispatcher dispatcher = Mockito.mock(PipelineDispatcher.class);
        SourceRepository sourceRepository = Mockito.mock(SourceRepository.class);
        OperationRuntimeRegistry runtimeRegistry = Mockito.mock(OperationRuntimeRegistry.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        AbsSender telegramSender = Mockito.mock(AbsSender.class);
        ResumableOperationHandler resumableHandler = Mockito.mock(ResumableOperationHandler.class);

        TelegramInteractionAnswerOrchestrator orchestrator = new TelegramInteractionAnswerOrchestrator(
                controlService,
                operationService,
                dispatcher,
                List.of(resumableHandler),
                sourceRepository,
                runtimeRegistry,
                JsonMapper.builder().build(),
                telegramSender,
                eventPublisher
        );

        Message callbackMessage = Mockito.mock(Message.class);
        when(callbackMessage.getChatId()).thenReturn(100L);
        when(callbackMessage.getMessageId()).thenReturn(500);

        CallbackQuery callback = Mockito.mock(CallbackQuery.class);
        when(callback.getData()).thenReturn("qa_yes");
        when(callback.getId()).thenReturn("cb-1");
        when(callback.getMessage()).thenReturn(callbackMessage);

        Update update = Mockito.mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callback);

        TelegramInboundEvent event = Mockito.mock(TelegramInboundEvent.class);
        when(event.getPayload()).thenReturn(update);

        TelegramQuestionQueueItem queueItem = new TelegramQuestionQueueItem();
        queueItem.setQuestionId("q-1");
        queueItem.setParentOperationId(BigInteger.ONE);
        queueItem.setStageExecutionId(BigInteger.valueOf(11));
        queueItem.setQuestionType("YesNoQuestion");
        queueItem.setQuestionPayloadJson("{\"type\":\"YES_NO\",\"message\":\"confirm\"}");
        queueItem.setQuestionMessageId(500);
        queueItem.setStatus(TelegramQuestionStatus.WAITING);

        when(controlService.findWaitingQuestionByMessage(100L, 500))
                .thenReturn(Optional.of(new TelegramResolvedQuestion(BigInteger.ONE, queueItem)));

        OperationExecutionEntity stageExecution = new OperationExecutionEntity();
        stageExecution.setId(BigInteger.valueOf(11));
        stageExecution.setParentId(BigInteger.ONE);
        stageExecution.setOperationType(OperationType.ORDER_CREATION);
        stageExecution.setStatus(OperationStatus.WAITING_INTERACTION);
        stageExecution.setCancelable(false);
        stageExecution.setInitiatorUserId(7L);
        when(operationService.getOperation(BigInteger.valueOf(11))).thenReturn(Optional.of(stageExecution));

        when(resumableHandler.supports(any(), any(), any())).thenReturn(true);
        when(resumableHandler.resume(any(), any(), any()))
                .thenThrow(new NeedUserInteractionException(new YesNoQuestion("retry")));

        UserEntity user = new UserEntity();
        user.setId(7L);

        boolean handled = orchestrator.tryHandle(event, user);

        assertTrue(handled);
        verify(controlService).markQuestionSuperseded(
                Mockito.eq(BigInteger.ONE),
                Mockito.eq("q-1"),
                Mockito.anyString()
        );
        verify(eventPublisher).publishEvent(argThat(e ->
                e instanceof QuestionRequiredEvent questionEvent
                        && BigInteger.ONE.equals(questionEvent.getParentOperationId())
                        && BigInteger.valueOf(11).equals(questionEvent.getStageExecutionId())
        ));
    }

    @Test
    void shouldMarkDuplicateQuestionFailedWhenNoResumableHandlerExists() {
        TelegramControlMessageService controlService = Mockito.mock(TelegramControlMessageService.class);
        OperationExecutionService operationService = Mockito.mock(OperationExecutionService.class);
        PipelineDispatcher dispatcher = Mockito.mock(PipelineDispatcher.class);
        SourceRepository sourceRepository = Mockito.mock(SourceRepository.class);
        OperationRuntimeRegistry runtimeRegistry = Mockito.mock(OperationRuntimeRegistry.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        AbsSender telegramSender = Mockito.mock(AbsSender.class);

        TelegramInteractionAnswerOrchestrator orchestrator = new TelegramInteractionAnswerOrchestrator(
                controlService,
                operationService,
                dispatcher,
                List.of(),
                sourceRepository,
                runtimeRegistry,
                JsonMapper.builder().build(),
                telegramSender,
                eventPublisher
        );

        Message callbackMessage = Mockito.mock(Message.class);
        when(callbackMessage.getChatId()).thenReturn(100L);
        when(callbackMessage.getMessageId()).thenReturn(500);

        CallbackQuery callback = Mockito.mock(CallbackQuery.class);
        when(callback.getData()).thenReturn("qa_yes");
        when(callback.getId()).thenReturn("cb-dup");
        when(callback.getMessage()).thenReturn(callbackMessage);

        Update update = Mockito.mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callback);

        TelegramInboundEvent event = Mockito.mock(TelegramInboundEvent.class);
        when(event.getPayload()).thenReturn(update);

        TelegramQuestionQueueItem queueItem = new TelegramQuestionQueueItem();
        queueItem.setQuestionId("q-dup");
        queueItem.setParentOperationId(BigInteger.ONE);
        queueItem.setStageExecutionId(BigInteger.valueOf(11));
        queueItem.setQuestionType("DuplicateQuestion");
        queueItem.setQuestionPayloadJson("{\"type\":\"DUPLICATE\",\"entityType\":\"ORDER\",\"entityId\":1,\"duplicateEntityType\":\"ORDER\",\"duplicateEntityId\":42}");
        queueItem.setQuestionMessageId(500);
        queueItem.setStatus(TelegramQuestionStatus.WAITING);

        when(controlService.findWaitingQuestionByMessage(100L, 500))
                .thenReturn(Optional.of(new TelegramResolvedQuestion(BigInteger.ONE, queueItem)));

        OperationExecutionEntity stageExecution = new OperationExecutionEntity();
        stageExecution.setId(BigInteger.valueOf(11));
        stageExecution.setParentId(BigInteger.ONE);
        stageExecution.setOperationType(OperationType.ORDER_CREATION);
        stageExecution.setStatus(OperationStatus.WAITING_INTERACTION);
        stageExecution.setCancelable(false);
        stageExecution.setInitiatorUserId(7L);
        when(operationService.getOperation(BigInteger.valueOf(11))).thenReturn(Optional.of(stageExecution));

        UserEntity user = new UserEntity();
        user.setId(7L);

        boolean handled = orchestrator.tryHandle(event, user);

        assertTrue(handled);
        verify(controlService).markQuestionFailed(
                Mockito.eq(BigInteger.ONE),
                Mockito.eq("q-dup"),
                Mockito.anyString()
        );
        verify(eventPublisher, never()).publishEvent(any(QuestionRequiredEvent.class));
    }
}
