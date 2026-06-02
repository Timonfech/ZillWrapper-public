package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;

public record DeliveryTargetSpec(
    ContactMethodType type,
    String value,
    OutputType format
) {}
