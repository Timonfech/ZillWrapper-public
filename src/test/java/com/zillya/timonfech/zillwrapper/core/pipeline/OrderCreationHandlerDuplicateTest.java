package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.OrderStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.exceptions.NeedUserInteractionException;
import com.zillya.timonfech.zillwrapper.core.interactions.answers.YesNoAnswer;
import com.zillya.timonfech.zillwrapper.core.interactions.questions.DuplicateQuestion;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodNormalizer;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.security.OrderSecurityService;
import com.zillya.timonfech.zillwrapper.core.services.OrderProcessingService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.ContactManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OrderCreationHandlerDuplicateTest {

    @Test
    void shouldThrowDuplicateQuestionWhenExactDuplicateExists() {
        OrderProcessingService orderProcessingService = Mockito.mock(OrderProcessingService.class);
        OrderSecurityService orderSecurityService = Mockito.mock(OrderSecurityService.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        ContactManagementService contactManagementService = Mockito.mock(ContactManagementService.class);

        OrderCreationHandler handler = new OrderCreationHandler(
                orderProcessingService,
                orderSecurityService,
                userRepository,
                orderRepository,
                contactManagementService,
                JsonMapper.builder().build(),
                new BusinessPeriodNormalizer(),
                Mockito.mock(OperationRuntimeRegistry.class)
        );

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u");
        user.setActive(true);
        user.setRole(UserEntity.Role.MANAGER);
        user.setQuotas(new java.util.HashSet<>());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity candidate = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        candidate.setId(42L);
        candidate.setOrderStatus(OrderStatus.PROCESSED);
        when(orderRepository.findAllByPortalId(100L)).thenReturn(List.of(candidate));
        when(orderRepository.findAllByWhiteAdminId(100L)).thenReturn(List.of());
        when(orderRepository.findAllByUserCommentNormalized("")).thenReturn(List.of());
        when(orderRepository.findById(42L)).thenReturn(Optional.of(candidate));
        when(orderRepository.hasEmailDeliveryTarget(42L, "test@example.com")).thenReturn(true);
        when(orderRepository.containsOrderItems(anyLong(), anyString())).thenReturn(true);

        ProductInfo product = new ProductInfo(4, 2, null, 1, Pattern.compile(".*"), Map.of("en_short", "ZAB"), Map.of(), List.of(KeyType.ONLINE));
        OrderOperationContext context = new OrderOperationContext(
                1L,
                100L,
                "test@example.com",
                List.of(new OrderItemSpec(product, 1, new BusinessPeriod(1, BusinessPeriodUnit.YEAR), 1, List.of())),
                List.of(),
                null
        );
        context.setInitiatorUserId(7L);

        NeedUserInteractionException ex = assertThrows(NeedUserInteractionException.class, () -> handler.handle(context));
        assertTrue(ex.getQuestion() instanceof DuplicateQuestion);
        DuplicateQuestion question = (DuplicateQuestion) ex.getQuestion();
        assertEquals(42L, question.duplicateEntityId());
    }

    @Test
    void shouldAcceptDuplicateWhenUserConfirmsYes() {
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity existing = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        existing.setId(42L);
        existing.setOrderStatus(OrderStatus.PROCESSED);
        existing.setItems(new java.util.ArrayList<>());
        when(orderRepository.findByIdWithItems(42L)).thenReturn(Optional.of(existing));

        OrderCreationHandler handler = new OrderCreationHandler(
                Mockito.mock(OrderProcessingService.class),
                Mockito.mock(OrderSecurityService.class),
                Mockito.mock(UserRepository.class),
                orderRepository,
                Mockito.mock(ContactManagementService.class),
                JsonMapper.builder().build(),
                new BusinessPeriodNormalizer(),
                runtimeRegistryWithContext()
        );

        OperationExecutionEntity stage = new OperationExecutionEntity();
        stage.setOperationType(OperationType.ORDER_CREATION);
        stage.setParentId(java.math.BigInteger.ONE);

        OperationResult<?> result = handler.resume(
                stage,
                new DuplicateQuestion("ORDER", 100L, "ORDER", 42L),
                new YesNoAnswer(true)
        );

        assertTrue(result.isSuccess());
        assertNull(result.getPayload());
        assertEquals(42L, stage.getEntityId());
    }

    @Test
    void shouldNotDetectDuplicateWhenEmailDiffers() {
        OrderProcessingService orderProcessingService = Mockito.mock(OrderProcessingService.class);
        when(orderProcessingService.createOrder(org.mockito.ArgumentMatchers.any())).thenReturn(501L);
        OrderSecurityService orderSecurityService = Mockito.mock(OrderSecurityService.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        ContactManagementService contactManagementService = Mockito.mock(ContactManagementService.class);

        OrderCreationHandler handler = new OrderCreationHandler(
                orderProcessingService,
                orderSecurityService,
                userRepository,
                orderRepository,
                contactManagementService,
                JsonMapper.builder().build(),
                new BusinessPeriodNormalizer(),
                Mockito.mock(OperationRuntimeRegistry.class)
        );

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u");
        user.setActive(true);
        user.setRole(UserEntity.Role.MANAGER);
        user.setQuotas(new java.util.HashSet<>());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity candidate = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        candidate.setId(42L);
        candidate.setOrderStatus(OrderStatus.PROCESSED);
        when(orderRepository.findAllByPortalId(100L)).thenReturn(List.of(candidate));
        when(orderRepository.findAllByWhiteAdminId(100L)).thenReturn(List.of());
        when(orderRepository.findById(42L)).thenReturn(Optional.of(candidate));
        when(orderRepository.hasEmailDeliveryTarget(42L, "test@example.com")).thenReturn(false);
        when(orderRepository.containsOrderItems(anyLong(), anyString())).thenReturn(true);

        ProductInfo product = new ProductInfo(4, 2, null, 1, Pattern.compile(".*"), Map.of("en_short", "ZAB"), Map.of(), List.of(KeyType.ONLINE));
        OrderOperationContext context = new OrderOperationContext(
                1L,
                100L,
                "test@example.com",
                List.of(new OrderItemSpec(product, 1, new BusinessPeriod(1, BusinessPeriodUnit.YEAR), 1, List.of())),
                List.of(),
                null
        );
        context.setInitiatorUserId(7L);

        OperationResult<?> result = handler.handle(context);
        assertTrue(result.isSuccess());
        assertNull(result.getPayload());
        assertEquals(501L, context.getOrderId());
    }

    @Test
    void shouldDetectDuplicateByUserCommentWhenEmailAndItemsMatch() {
        OrderProcessingService orderProcessingService = Mockito.mock(OrderProcessingService.class);
        OrderSecurityService orderSecurityService = Mockito.mock(OrderSecurityService.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        ContactManagementService contactManagementService = Mockito.mock(ContactManagementService.class);

        OrderCreationHandler handler = new OrderCreationHandler(
                orderProcessingService,
                orderSecurityService,
                userRepository,
                orderRepository,
                contactManagementService,
                JsonMapper.builder().build(),
                new BusinessPeriodNormalizer(),
                Mockito.mock(OperationRuntimeRegistry.class)
        );

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u");
        user.setActive(true);
        user.setRole(UserEntity.Role.MANAGER);
        user.setQuotas(new java.util.HashSet<>());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity candidate = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        candidate.setId(77L);
        candidate.setOrderStatus(OrderStatus.PROCESSED);
        when(orderRepository.findAllByUserCommentNormalized("abc123")).thenReturn(List.of(candidate));
        when(orderRepository.findById(77L)).thenReturn(Optional.of(candidate));
        when(orderRepository.hasEmailDeliveryTarget(77L, "test@example.com")).thenReturn(true);
        when(orderRepository.containsOrderItems(org.mockito.ArgumentMatchers.eq(77L), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        ProductInfo product = new ProductInfo(4, 2, null, 1, Pattern.compile(".*"), Map.of("en_short", "ZAB"), Map.of(), List.of(KeyType.ONLINE));
        OrderOperationContext context = new OrderOperationContext(
                1L,
                null,
                "test@example.com",
                List.of(new OrderItemSpec(product, 1, new BusinessPeriod(1, BusinessPeriodUnit.YEAR), 1, List.of())),
                List.of(),
                null
        );
        context.setUserComment("abc123");
        context.setInitiatorUserId(7L);

        NeedUserInteractionException ex = assertThrows(NeedUserInteractionException.class, () -> handler.handle(context));
        DuplicateQuestion question = (DuplicateQuestion) ex.getQuestion();
        assertEquals(77L, question.duplicateEntityId());
    }

    private OperationRuntimeRegistry runtimeRegistryWithContext() {
        OperationRuntimeRegistry runtimeRegistry = Mockito.mock(OperationRuntimeRegistry.class);
        ProductInfo product = new ProductInfo(
                2,
                4,
                null,
                1,
                Pattern.compile(".*"),
                Map.of("en_short", "ZAB"),
                Map.of(),
                List.of(KeyType.ONLINE)
        );
        OrderOperationContext orderCtx = new OrderOperationContext(
                1L,
                100L,
                "test@example.com",
                List.of(new OrderItemSpec(product, 1, new BusinessPeriod(1, BusinessPeriodUnit.YEAR), 1, List.of(), List.of(KeyType.ONLINE), false, OrderItemOptions.empty())),
                List.of(),
                null
        );
        when(runtimeRegistry.load(java.math.BigInteger.ONE)).thenReturn(Optional.of(orderCtx));
        return runtimeRegistry;
    }
}
