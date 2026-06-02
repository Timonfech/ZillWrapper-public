package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;

public record AsyncStageCompletion(OperationStatus status, String summary) {
}

