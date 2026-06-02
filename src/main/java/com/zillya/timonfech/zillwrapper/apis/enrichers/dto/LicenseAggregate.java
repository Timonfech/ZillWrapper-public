package com.zillya.timonfech.zillwrapper.apis.enrichers.dto;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;

public record LicenseAggregate(
        LicenseEntity license,
        ClientEntity client,
        OrderEntity order

) {}