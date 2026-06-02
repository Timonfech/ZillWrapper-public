package com.zillya.timonfech.zillwrapper.core.pipeline.plan;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;

public interface OperationPlanBuilder {
    boolean supports(OperationType rootType, OrderOperationContext context);

    ExecutionPlan build(OperationType rootType, OrderOperationContext context);
}

