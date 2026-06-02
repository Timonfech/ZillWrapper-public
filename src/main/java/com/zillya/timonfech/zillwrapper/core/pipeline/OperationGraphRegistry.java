package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.ExecutionPlan;
import com.zillya.timonfech.zillwrapper.core.pipeline.plan.OperationPlanBuilder;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OperationGraphRegistry {
    private final List<OperationPlanBuilder> builders;

    public List<OperationType> stagesForExecution(OperationType rootType, OrderOperationContext context) {
        return buildExecutionPlan(rootType, context).steps().stream()
                .map(step -> step.stageType())
                .toList();
    }

    public ExecutionPlan buildExecutionPlan(OperationType rootType, OrderOperationContext context) {
        for (OperationPlanBuilder builder : builders) {
            if (!builder.supports(rootType, context)) {
                continue;
            }
            ExecutionPlan plan = builder.build(rootType, context);
            validate(rootType, plan);
            return plan;
        }
        throw new IllegalStateException("No OperationPlanBuilder found for rootType=" + rootType);
    }

    private void validate(OperationType rootType, ExecutionPlan plan) {
        if (plan == null || plan.steps().isEmpty()) {
            throw new IllegalStateException("ExecutionPlan is empty for rootType=" + rootType);
        }
    }
}
