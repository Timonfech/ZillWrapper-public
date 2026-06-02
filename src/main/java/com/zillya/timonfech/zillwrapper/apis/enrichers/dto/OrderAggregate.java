package com.zillya.timonfech.zillwrapper.apis.enrichers.dto;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;

import javax.annotation.Nullable;
import java.util.List;

public record OrderAggregate(
        OrderEntity order,
        ClientEntity client,
        List<ContactMethod> contacts,
        List<OrderItemEntity> items,
        @Nullable
        LegalEntityInfo legalEntityInfo
) {
}