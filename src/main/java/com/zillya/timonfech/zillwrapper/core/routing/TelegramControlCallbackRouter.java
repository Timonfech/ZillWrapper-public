package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.TelegramControlCallbackOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class TelegramControlCallbackRouter implements IntentRouter<TelegramInboundEvent> {

    private final TelegramControlCallbackOrchestrator callbackOrchestrator;

    @Override
    public boolean canRoute(InboundEvent<?> event) {
        if (!(event instanceof TelegramInboundEvent tgEvent)) {
            return false;
        }
        return callbackOrchestrator.canHandle(tgEvent);
    }

    @Override
    public RoutingDecision route(TelegramInboundEvent event) {
        return new RoutingDecision.ControlCallbackDecision(event);
    }
}

