package com.zillya.timonfech.zillwrapper.core.subscription.warnings;

import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionWarningSourceRouter {

    private final List<SubscriptionSourceWarningHandler> handlers;

    public WarningDeliveryResult route(SubscriptionWarningDeliveryEntity delivery) {
        SubscriptionSourceWarningHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(delivery.getSourceId()))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            String msg = "No warning source handler for sourceId=" + delivery.getSourceId();
            log.warn(msg);
            return WarningDeliveryResult.fail(msg);
        }
        return handler.sendWarning(delivery);
    }
}
