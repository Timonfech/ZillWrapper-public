package com.zillya.timonfech.zillwrapper.core.pending;

import com.zillya.timonfech.zillwrapper.core.communication.InteractionBindingService;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramControlMessageService;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskEntity;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskStatus;
import com.zillya.timonfech.zillwrapper.core.entities.pending.PendingTaskType;
import com.zillya.timonfech.zillwrapper.core.pipeline.OperationGraphRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.pipeline.PipelineDispatcher;
import com.zillya.timonfech.zillwrapper.core.pipeline.WhiteAdminPlaceholderOrderService;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingTaskExecutorBindingOrderTest {

    @Mock private PendingTaskService pendingTaskService;
    @Mock private InteractionBindingService interactionBindingService;
    @Mock private UserRepository userRepository;
    @Mock private TelegramOperationBindingRepository telegramOperationBindingRepository;
    @Mock private ProductRegistry productRegistry;
    @Mock private OperationExecutionService operationExecutionService;
    @Mock private OperationRuntimeRegistry runtimeRegistry;
    @Mock private PipelineDispatcher pipelineDispatcher;
    @Mock private OperationGraphRegistry operationGraphRegistry;
    @Mock private WhiteAdminPlaceholderOrderService whiteAdminPlaceholderOrderService;
    @Mock private TelegramControlMessageService telegramControlMessageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void shouldBindPreviewToOperationBeforeDispatch() {
        PendingTaskExecutor executor = new PendingTaskExecutor(
                pendingTaskService,
                interactionBindingService,
                userRepository,
                telegramOperationBindingRepository,
                operationExecutionService,
                runtimeRegistry,
                pipelineDispatcher,
                operationGraphRegistry,
                whiteAdminPlaceholderOrderService,
                telegramControlMessageService,
                eventPublisher,
                new ObjectMapper(),
                productRegistry
        );

        PendingTaskEntity task = new PendingTaskEntity();
        task.setTaskId("preview-1");
        task.setTaskType(PendingTaskType.ORDER_PREVIEW_CONFIRMATION);
        task.setStatus(PendingTaskStatus.WAITING);
        task.setInitiatorUserId(11L);
        task.setSourceActorId("123");
        task.setExpiresAt(Instant.now().plusSeconds(60));

        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(BigInteger.valueOf(99));
        binding.setPreviewStatus("WAITING");
        binding.setActivePreviewId("preview-1");

        OrderOperationContext context = new OrderOperationContext(1L, 123L, "client@example.com", List.of(), List.of(), null);
        context.setOperationId(BigInteger.valueOf(99));

        com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity user =
                new com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity();
        user.setId(11L);

        com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity parent =
                new com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity();
        parent.setId(BigInteger.valueOf(99));

        when(pendingTaskService.get("preview-1")).thenReturn(Optional.of(task));
        when(interactionBindingService.resolveActiveTask(eq("preview-1"), any(), any())).thenReturn(Optional.of(binding));
        when(runtimeRegistry.load(BigInteger.valueOf(99))).thenReturn(Optional.of(context));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(operationExecutionService.getOperation(BigInteger.valueOf(99))).thenReturn(Optional.of(parent));

        boolean ok = executor.confirm("preview-1", "123", 100L, 500);

        assertTrue(ok);
        InOrder inOrder = inOrder(interactionBindingService, pipelineDispatcher);
        inOrder.verify(interactionBindingService).bindOperationToTask("preview-1", BigInteger.valueOf(99));
        inOrder.verify(pipelineDispatcher).dispatch(any());
    }
}
