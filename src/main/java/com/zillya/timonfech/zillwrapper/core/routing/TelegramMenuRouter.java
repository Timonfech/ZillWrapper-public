package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.TelegramEnrichmentMenuOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
public class TelegramMenuRouter implements IntentRouter<TelegramInboundEvent> {

    private final TelegramEnrichmentMenuOrchestrator telegramEnrichmentMenuOrchestrator;

    @Override
    public boolean canRoute(InboundEvent<?> event) {
        if (!(event instanceof TelegramInboundEvent tgEvent)) {
            return false;
        }
        return telegramEnrichmentMenuOrchestrator.canHandle(tgEvent);
    }

    @Override
    public RoutingDecision route(TelegramInboundEvent event) {
        return new RoutingDecision.MenuDecision(event);
    }
}
