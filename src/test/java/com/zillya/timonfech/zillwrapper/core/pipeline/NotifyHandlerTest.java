package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.communication.EmailNotifyService;
import com.zillya.timonfech.zillwrapper.core.communication.EmailSendResult;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.security.OrderSecurityService;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyHandlerTest {

    @Test
    void shouldDeliverByEmailAndMarkSent() {
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OrderSecurityService orderSecurityService = Mockito.mock(OrderSecurityService.class);
        EmailNotifyService emailNotifyService = Mockito.mock(EmailNotifyService.class);
        OperationExecutionService operationExecutionService = Mockito.mock(OperationExecutionService.class);

        NotifyHandler handler = new NotifyHandler(orderRepository, orderItemRepository, orderSecurityService, emailNotifyService, operationExecutionService);

        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setItems(new ArrayList<>());
        order.setDeliveryTargets(new ArrayList<>());
        order.setOrderStatus(OrderStatus.NEW);
        when(orderRepository.findByIdWithDeliveryTargets(10L)).thenReturn(Optional.of(order));

        OrderItemEntity item = new OrderItemEntity();
        item.setId(101L);
        item.setOrderId(10L);
        item.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.YEAR));
        item.setProcessingStatus(ItemProcessingStatus.ARTIFACTS_READY);
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));

        EmailSendResult sendResult = new EmailSendResult();
        sendResult.markDelivered(101L);
        when(emailNotifyService.sendOrderItems(eq(order), anyList(), anyList(), anyMap(), any(OperationType.class), any(Locale.class))).thenReturn(sendResult);

        OrderOperationContext context = new OrderOperationContext(1L, 99L, "a@b.c", List.of(), List.of(), null);
        context.setOrderId(10L);
        context.setInitiatorUserId(7L);
        context.setLocaleTag("en");

        OperationResult<?> result = handler.handle(context);

        assertTrue(result.isSuccess());
        assertEquals(OrderStatus.SENT, order.getOrderStatus());
        assertEquals(ItemProcessingStatus.DELIVERED, item.getProcessingStatus());
        verify(orderSecurityService).confirmQuota(7L, 10L);

        ArgumentCaptor<Locale> localeCaptor = ArgumentCaptor.forClass(Locale.class);
        verify(emailNotifyService).sendOrderItems(eq(order), anyList(), anyList(), anyMap(), any(OperationType.class), localeCaptor.capture());
        assertEquals(Locale.ENGLISH.getLanguage(), localeCaptor.getValue().getLanguage());
        verify(orderRepository).findByIdWithDeliveryTargets(10L);
    }

    @Test
    void shouldFailWhenEmailDeliveryFails() {
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OrderSecurityService orderSecurityService = Mockito.mock(OrderSecurityService.class);
        EmailNotifyService emailNotifyService = Mockito.mock(EmailNotifyService.class);
        OperationExecutionService operationExecutionService = Mockito.mock(OperationExecutionService.class);

        NotifyHandler handler = new NotifyHandler(orderRepository, orderItemRepository, orderSecurityService, emailNotifyService, operationExecutionService);

        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setItems(new ArrayList<>());
        order.setDeliveryTargets(new ArrayList<>());
        when(orderRepository.findByIdWithDeliveryTargets(10L)).thenReturn(Optional.of(order));

        OrderItemEntity item = new OrderItemEntity();
        item.setId(102L);
        item.setOrderId(10L);
        item.setProcessingStatus(ItemProcessingStatus.ARTIFACTS_READY);
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));

        EmailSendResult sendResult = new EmailSendResult();
        sendResult.markFailed(102L, "smtp failed");
        when(emailNotifyService.sendOrderItems(eq(order), anyList(), anyList(), anyMap(), any(OperationType.class), any(Locale.class))).thenReturn(sendResult);

        OrderOperationContext context = new OrderOperationContext(1L, 99L, "a@b.c", List.of(), List.of(), null);
        context.setOrderId(10L);
        context.setInitiatorUserId(7L);

        OperationResult<?> result = handler.handle(context);

        assertFalse(result.isSuccess());
        assertEquals(ItemProcessingStatus.FAILED, item.getProcessingStatus());
        assertEquals(OrderStatus.FAILED, order.getOrderStatus());
    }
}
