package com.zillya.timonfech.zillwrapper.core.subscription.notifications;

import com.zillya.timonfech.zillwrapper.core.subscription.events.SubscriptionDetailedDeltaEvent;

public interface SubscriptionSourceNotificationHandler {

    boolean supports(Long sourceId);

    void notifyDetailedDelta(SubscriptionDetailedDeltaEvent event);
}

