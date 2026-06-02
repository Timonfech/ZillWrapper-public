package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;

/**
 * Extension point for artifact generation strategies.
 */
public interface OrderArtifactGenerator {
    boolean supports(OrderOperationContext context);
    OperationResult<?> generate(OrderOperationContext context);
}
