package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.TelegramInteractionAnswerOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@RequiredArgsConstructor
public class TelegramInteractionRouter implements IntentRouter<TelegramInboundEvent> {

    private final TelegramInteractionAnswerOrchestrator telegramInteractionAnswerOrchestrator;

    @Override
    public boolean canRoute(InboundEvent<?> event) {
        if (!(event instanceof TelegramInboundEvent tgEvent)) {
            return false;
        }
        if (tgEvent.getPayload() != null
                && tgEvent.getPayload().hasEditedMessage()
                && tgEvent.getPayload().getEditedMessage() != null
                && tgEvent.getPayload().getEditedMessage().hasText()) {
            return true;
        }
        return telegramInteractionAnswerOrchestrator.canHandle(tgEvent);
    }

    @Override
    public RoutingDecision route(TelegramInboundEvent event) {
        return new RoutingDecision.InteractionDecision(event);
    }
}
