package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.interfaces.AsyncOperationHandler;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacySyncHandler implements AsyncOperationHandler<IOperationContext> {

    private final LegacyOrderCommentSyncService legacySyncService;
    private final LegacySyncDecisionService decisionService;
    private final OrderRepository orderRepository;
    @Qualifier("pipelineTaskExecutor")
    private final Executor pipelineTaskExecutor;

    @Override
    public String name() {
        return "LEGACY_SYNC";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.LEGACY_SYNC;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.LEGACY_SYNC
                && context instanceof OrderOperationContext;
    }

    @OperationStep(type = OperationType.LEGACY_SYNC, stepProps = {
            OperationStep.Props.ASYNC,
            OperationStep.Props.INTERACTIVE
    })
    @Override
    public CompletionStage<OperationResult<?>> handleAsync(IOperationContext context) {
        OrderOperationContext orderCtx = (OrderOperationContext) context;
        if (orderCtx.getOrderId() == null || orderCtx.getStageExecutionId() == null || orderCtx.getOperationId() == null) {
            return CompletableFuture.completedFuture(OperationResult.ok(
                    new AsyncStageCompletion(OperationStatus.PARTIALLY_DONE, "Legacy sync skipped: missing runtime ids")));
        }
        Long orderId = orderCtx.getOrderId();
        return CompletableFuture.supplyAsync(() -> runLegacySyncAsync(orderId), pipelineTaskExecutor);
    }

    @Override
    public OperationResult<?> handle(IOperationContext context) {
        return OperationResult.fail("Use async handler contract for LEGACY_SYNC", false);
    }

    private OperationResult<?> runLegacySyncAsync(Long orderId) {
        try {
            var order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                return OperationResult.ok(new AsyncStageCompletion(
                        OperationStatus.PARTIALLY_DONE, "Legacy sync skipped: order not found"));
            }
            if (order.getWhiteAdminId() == null) {
                return OperationResult.ok(new AsyncStageCompletion(
                        OperationStatus.PARTIALLY_DONE, "Legacy sync skipped: order has no whiteAdminId"));
            }

            LegacySyncDecision effectiveDecision = decisionService.decide(order);

            var outcome = legacySyncService.sync(order, effectiveDecision);
            String warning = mergeWarnings(effectiveDecision.reason(), outcome.warning());
            warning = normalizeLegacySyncWarning(warning);
            OperationStatus status = (warning == null || warning.isBlank())
                    ? OperationStatus.DONE
                    : OperationStatus.PARTIALLY_DONE;
            return OperationResult.ok(new AsyncStageCompletion(
                    status,
                    warning == null || warning.isBlank() ? "Legacy sync completed" : "Legacy sync warning: " + warning
            ));
        } catch (Exception ex) {
            log.warn("Legacy sync failed for orderId={}: {}", orderId, ex.getMessage());
            return OperationResult.ok(new AsyncStageCompletion(
                    OperationStatus.PARTIALLY_DONE, "Legacy sync warning: " + ex.getMessage()));
        }
    }

    private String mergeWarnings(String left, String right) {
        boolean leftBlank = left == null || left.isBlank();
        boolean rightBlank = right == null || right.isBlank();
        if (leftBlank && rightBlank) {
            return null;
        }
        if (leftBlank) {
            return right;
        }
        if (rightBlank) {
            return left;
        }
        if (left.equals(right)) {
            return left;
        }
        return left + "; " + right;
    }

    private String normalizeLegacySyncWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            return null;
        }
        String cleaned = warning.replace("TODO_INVOICE_POLICY", "").trim();
        if (cleaned.startsWith(";")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }

}
