package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.CompletionStage;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncStageCoordinator {

    private final OperationExecutionService operationExecutionService;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final ObjectProvider<PipelineDispatcher> pipelineDispatcherProvider;
    private final StageCompletionNotifier stageCompletionNotifier;

    public void attach(OrderOperationContext ctx,
                       OperationType stageType,
                       CompletionStage<OperationResult<?>> stageFuture,
                       boolean crucialStage) {
        BigInteger parentOperationId = ctx.getOperationId();
        BigInteger stageExecutionId = ctx.getStageExecutionId();
        Long orderId = ctx.getOrderId();

        stageFuture.whenComplete((result, throwable) -> {
            AsyncStageCompletion completion = resolveCompletion(result, throwable);
            OperationStatus status = completion.status();
            String summary = completion.summary();
            operationExecutionService.markStageCompleted(stageExecutionId, status, summary);
            boolean interactiveEnabled = operationExecutionService.getOperation(stageExecutionId)
                    .map(com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity::isInteractionEnabled)
                    .orElseGet(() -> operationExecutionService.getRootOperation(parentOperationId)
                            .map(com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity::isInteractionEnabled)
                            .orElse(false));
            stageCompletionNotifier.notifyStageCompletion(new StageCompletionNotification(
                    parentOperationId,
                    stageExecutionId,
                    stageType,
                    status,
                    summary,
                    interactiveEnabled
            ));

            if (status == OperationStatus.FAILED) {
                operationExecutionService.markParentFailed(parentOperationId, summary);
                return;
            }
            if (crucialStage) {
                continuePipeline(parentOperationId, orderId, stageType);
            }
        });
    }

    private void continuePipeline(BigInteger parentOperationId,
                                  Long orderId,
                                  OperationType stageType) {
        PipelineDispatcher dispatcher = pipelineDispatcherProvider.getIfAvailable();
        if (dispatcher == null) {
            log.warn("Async stage continuation skipped: PipelineDispatcher unavailable parentOpId={} stageType={}",
                    parentOperationId,
                    stageType);
            return;
        }
        runtimeRegistry.load(parentOperationId).ifPresentOrElse(ctx -> {
            ctx.setOperationId(parentOperationId);
            ctx.setOrderId(orderId);
            ctx.setCurrentStage(stageType);
            dispatcher.dispatch(ctx);
        }, () -> {
            log.warn("Async stage continuation skipped: runtime context missing parentOpId={} stageType={}",
                    parentOperationId,
                    stageType);
        });
    }

    private AsyncStageCompletion resolveCompletion(OperationResult<?> result, Throwable throwable) {
        if (throwable != null) {
            String msg = throwable.getMessage() == null ? "Async stage failed" : throwable.getMessage();
            return new AsyncStageCompletion(OperationStatus.FAILED, msg);
        }
        if (result == null) {
            return new AsyncStageCompletion(OperationStatus.FAILED, "Async stage returned null result");
        }
        if (result.getPayload() instanceof AsyncStageCompletion completion && completion.status() != null) {
            String summary = completion.summary() == null || completion.summary().isBlank()
                    ? "Async stage completed"
                    : completion.summary();
            return new AsyncStageCompletion(completion.status(), summary);
        }
        if (!result.isSuccess()) {
            return new AsyncStageCompletion(OperationStatus.FAILED,
                    result.getErrorMessage() == null ? "Async stage failed" : result.getErrorMessage());
        }
        String summary = result.getPayload() == null ? "Async stage completed" : String.valueOf(result.getPayload());
        return new AsyncStageCompletion(OperationStatus.DONE, summary);
    }

}
