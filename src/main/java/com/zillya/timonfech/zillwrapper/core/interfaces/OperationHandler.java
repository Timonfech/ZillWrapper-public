package com.zillya.timonfech.zillwrapper.core.interfaces;


import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;

import java.util.Optional;


public interface OperationHandler <T>{
    String name();
    boolean supports(T t);
    OperationResult<?> handle(T t) throws OperationCancelledException;

    default OperationType handledOperationType() {
        return null;
    }

    default Class<? extends IOperationContext> requiredContextType() {
        return IOperationContext.class;
    }

    default Optional<OrderOperationContext> asOrderContext(T context) {
        if (context instanceof OrderOperationContext orderContext) {
            return Optional.of(orderContext);
        }
        return Optional.empty();
    }
}
