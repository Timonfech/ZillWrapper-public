package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

import java.math.BigInteger;

public record StageCompletionNotification(
        BigInteger parentOperationId,
        BigInteger stageExecutionId,
        OperationType operationType,
        OperationStatus status,
        String summary,
        boolean interactiveEnabled
) {
}

