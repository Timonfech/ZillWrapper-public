package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.interactions.commands.CommandIntent;
import com.zillya.timonfech.zillwrapper.core.routing.RoutingDecision;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InteractiveDecisionCoordinator {
    private final TelegramInteractionAnswerOrchestrator interactionAnswerOrchestrator;
    private final TelegramPreviewEditOrchestrator previewEditOrchestrator;
    private final TelegramEnrichmentMenuOrchestrator enrichmentMenuOrchestrator;
    private final TelegramSearchActionOrchestrator searchActionOrchestrator;
    private final TelegramControlCallbackOrchestrator controlCallbackOrchestrator;

    public RoutingDecisionExecutor.DecisionExecutionResult handle(RoutingDecision decision, UserEntity user) {
        if (decision instanceof RoutingDecision.ControlCallbackDecision callbackDecision) {
            boolean handled = controlCallbackOrchestrator.tryHandle(callbackDecision.event(), user);
            return handled ? RoutingDecisionExecutor.DecisionExecutionResult.ROUTED : RoutingDecisionExecutor.DecisionExecutionResult.IGNORED;
        }
        if (decision instanceof RoutingDecision.MenuDecision menuDecision) {
            boolean handled = enrichmentMenuOrchestrator.tryHandle(menuDecision.event(), user);
            return handled ? RoutingDecisionExecutor.DecisionExecutionResult.ROUTED : RoutingDecisionExecutor.DecisionExecutionResult.IGNORED;
        }
        if (decision instanceof RoutingDecision.InteractionDecision interactionDecision) {
            boolean handled = interactionAnswerOrchestrator.tryHandle(interactionDecision.event(), user);
            if (!handled && interactionDecision.event() instanceof TelegramInboundEvent tgEvent) {
                handled = previewEditOrchestrator.tryHandleEditedMessage(tgEvent);
            }
            return handled ? RoutingDecisionExecutor.DecisionExecutionResult.ROUTED : RoutingDecisionExecutor.DecisionExecutionResult.IGNORED;
        }
        if (decision instanceof RoutingDecision.SearchDecision searchDecision) {
            CommandIntent original = searchDecision.intent();
            CommandIntent normalized = CommandIntent.builder()
                    .sourceId(original.sourceId())
                    .operationType(original.operationType())
                    .entityType(original.entityType())
                    .payload(original.payload())
                    .chatId(original.chatId())
                    .messageThreadId(original.messageThreadId())
                    .messageId(original.messageId())
                    .replyToMessageId(original.replyToMessageId())
                    .actorUserId(user.getId())
                    .build();
            boolean handled = searchActionOrchestrator.handleSearchIntent(normalized, user);
            return handled ? RoutingDecisionExecutor.DecisionExecutionResult.ROUTED : RoutingDecisionExecutor.DecisionExecutionResult.IGNORED;
        }
        return RoutingDecisionExecutor.DecisionExecutionResult.IGNORED;
    }
}
