package com.zillya.timonfech.zillwrapper.core.subscription.notifications;

import com.zillya.timonfech.zillwrapper.core.subscription.events.SubscriptionDetailedDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionNotificationRouter {

    private final List<SubscriptionSourceNotificationHandler> handlers;

    @EventListener
    public void onDetailedDelta(SubscriptionDetailedDeltaEvent event) {
        SubscriptionSourceNotificationHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(event.getSourceId()))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            log.debug("No subscription source handler for sourceId={} orderId={} licenseId={}",
                    event.getSourceId(),
                    event.getOrderId(),
                    event.getLicenseId());
            return;
        }
        handler.notifyDetailedDelta(event);
    }
}

