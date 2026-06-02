package com.zillya.timonfech.zillwrapper.core.subscription.warnings;

import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;

public interface SubscriptionSourceWarningHandler {

    boolean supports(Long sourceId);

    WarningDeliveryResult sendWarning(SubscriptionWarningDeliveryEntity delivery);
}
