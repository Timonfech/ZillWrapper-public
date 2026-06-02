package com.zillya.timonfech.zillwrapper.core.runtime;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.events.OperationCompletedEvent;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OperationRuntimeRegistry {

    private final Map<BigInteger, OrderOperationContext> states = new ConcurrentHashMap<>();

    public void createForOperation(BigInteger operationId, OrderOperationContext context) {
        if (operationId == null || context == null) {
            return;
        }
        states.putIfAbsent(operationId, context);
    }

    public void save(BigInteger operationId, OrderOperationContext context) {
        if (operationId == null || context == null) {
            return;
        }
        states.put(operationId, context);
    }

    public Optional<OrderOperationContext> load(BigInteger operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(states.get(operationId));
    }

    public void patchAfterStageResult(BigInteger operationId,
                                      Long orderId,
                                      OperationType currentStage,
                                      Boolean skipDuplicateCheck) {
        load(operationId).ifPresent(ctx -> {
            if (orderId != null) {
                ctx.setOrderId(orderId);
            }
            if (currentStage != null) {
                ctx.setCurrentStage(currentStage);
            }
            if (skipDuplicateCheck != null) {
                ctx.setSkipDuplicateCheck(skipDuplicateCheck);
            }
            save(operationId, ctx);
        });
    }

    public void remove(BigInteger operationId) {
        if (operationId == null) {
            return;
        }
        states.remove(operationId);
    }

    @EventListener
    public void onOperationCompleted(OperationCompletedEvent event) {
        if (event == null || event.getRootOperation() == null) {
            return;
        }
        remove(event.getRootOperation().getId());
    }
}
