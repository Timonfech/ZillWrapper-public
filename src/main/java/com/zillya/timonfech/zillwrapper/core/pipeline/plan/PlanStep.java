package com.zillya.timonfech.zillwrapper.core.pipeline.plan;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

public record PlanStep(
        OperationType stageType,
        int sequenceNo,
        Boolean cancelable,
        Boolean nonBlocking
) {
}

