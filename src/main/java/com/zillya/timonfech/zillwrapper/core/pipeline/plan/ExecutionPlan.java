package com.zillya.timonfech.zillwrapper.core.pipeline.plan;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

import java.util.List;

public record ExecutionPlan(
        OperationType rootType,
        List<PlanStep> steps
) {
    public ExecutionPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

