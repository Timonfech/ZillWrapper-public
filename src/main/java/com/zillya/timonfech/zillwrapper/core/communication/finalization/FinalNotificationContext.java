package com.zillya.timonfech.zillwrapper.core.communication.finalization;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;

import java.util.List;
import java.util.Locale;

public record FinalNotificationContext(
        OperationExecutionEntity parentOperation,
        TelegramOperationBindingEntity binding,
        boolean newOrderCreated,
        Long orderId,
        OrderEntity order,
        List<OrderItemEntity> items,
        List<LicenseEntity> licenses,
        List<String> stageWarnings,
        List<String> nonCriticalWarnings,
        Locale locale
) {
}
