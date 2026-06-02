package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.communication.finalization.FinalNotificationPolicy;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.events.OperationCompletedEvent;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.interactions.HandlingInfoEvent;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseSubscriptionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import com.zillya.timonfech.zillwrapper.core.communication.sections.ControlMessageComposer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;
import org.telegram.telegrambots.meta.bots.AbsSender;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramFinalNotificationPolicyOrchestrationTest {

    private TelegramControlMessageService service;
    private TelegramOperationBindingRepository bindingRepository;
    private OperationExecutionService operationExecutionService;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private LicenseRepository licenseRepository;
    private LicenseSubscriptionRepository licenseSubscriptionRepository;
    private FinalNotificationPolicy policy;
    private TelegramBindingUpdateService bindingUpdateService;

    @BeforeEach
    void setUp() {
        bindingRepository = Mockito.mock(TelegramOperationBindingRepository.class);
        operationExecutionService = Mockito.mock(OperationExecutionService.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        orderItemRepository = Mockito.mock(OrderItemRepository.class);
        licenseRepository = Mockito.mock(LicenseRepository.class);
        licenseSubscriptionRepository = Mockito.mock(LicenseSubscriptionRepository.class);
        policy = Mockito.mock(FinalNotificationPolicy.class);
        bindingUpdateService = Mockito.mock(TelegramBindingUpdateService.class);
        ControlMessageComposer controlMessageComposer = Mockito.mock(ControlMessageComposer.class);
        Executor executor = Runnable::run;

        service = new TelegramControlMessageService(
                Mockito.mock(AbsSender.class),
                operationExecutionService,
                bindingRepository,
                orderRepository,
                orderItemRepository,
                licenseRepository,
                licenseSubscriptionRepository,
                List.of(policy),
                bindingUpdateService,
                controlMessageComposer,
                JsonMapper.builder().build(),
                new StaticMessageSource(),
                executor
        );
    }

    @Test
    void shouldApplyFinalPolicyAndMarkBinding() {
        OperationExecutionEntity parent = parentDone();
        OperationExecutionEntity orderStage = orderCreationStageDone(parent.getId(), 55L);

        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(parent.getId());
        binding.setChatId(100L);
        binding.setControlMessageId(1);
        binding.setQuestionQueueJson("[]");

        when(bindingRepository.findByOperationId(parent.getId())).thenReturn(Optional.of(binding));
        when(operationExecutionService.getChildren(parent.getId())).thenReturn(List.of(orderStage));
        when(orderRepository.findByIdWithDeliveryTargets(55L)).thenReturn(Optional.of(new OrderEntity()));
        when(orderItemRepository.findByOrderId(55L)).thenReturn(List.of());
        when(licenseRepository.findByOrderId(55L)).thenReturn(List.of());
        when(policy.supports(any())).thenReturn(true);
        when(policy.kind()).thenReturn("NEW_ORDER_RICH");

        service.onOperationCompleted(new OperationCompletedEvent(this, parent));

        verify(policy).notify(any());
        verify(bindingUpdateService).applyByOperationId(eq(parent.getId()), eq("final_success_store"), any());
    }

    @Test
    void shouldSkipWhenFinalAlreadyNotified() {
        OperationExecutionEntity parent = parentDone();
        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(parent.getId());
        binding.setChatId(100L);
        binding.setControlMessageId(1);
        binding.setQuestionQueueJson("[]");
        binding.setFinalNotifiedAt(Instant.now());

        when(bindingRepository.findByOperationId(parent.getId())).thenReturn(Optional.of(binding));

        service.onOperationCompleted(new OperationCompletedEvent(this, parent));

        verify(policy, never()).notify(any());
        verify(bindingUpdateService, never()).applyByOperationId(any(), anyString(), any());
    }

    @Test
    void shouldNotApplyFinalPolicyForNonDoneStatus() {
        OperationExecutionEntity parent = new OperationExecutionEntity();
        parent.setId(BigInteger.TEN);
        parent.setStatus(OperationStatus.FAILED);
        parent.setOperationType(OperationType.ORDER_CREATION);

        service.onOperationCompleted(new OperationCompletedEvent(this, parent));

        verify(policy, never()).notify(any());
        verify(bindingUpdateService, never()).applyByOperationId(any(), anyString(), any());
    }

    private OperationExecutionEntity parentDone() {
        OperationExecutionEntity parent = new OperationExecutionEntity();
        parent.setId(BigInteger.ONE);
        parent.setStatus(OperationStatus.DONE);
        parent.setOperationType(OperationType.ORDER_CREATION);
        return parent;
    }

    private OperationExecutionEntity orderCreationStageDone(BigInteger parentId, Long orderId) {
        OperationExecutionEntity child = new OperationExecutionEntity();
        child.setParentId(parentId);
        child.setOperationType(OperationType.ORDER_CREATION);
        child.setStatus(OperationStatus.DONE);
        child.setEntityId(orderId);
        return child;
    }
}
