package com.zillya.timonfech.zillwrapper.apis;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;

import java.util.concurrent.atomic.AtomicBoolean;

public interface EntityEnricher {
    boolean supports(EnrichmentRequest request);
    IEntityWithStatus<?> targetType();
    OperationResult<?> handle(EnrichmentRequest request, AtomicBoolean isCancelled) throws OperationCancelledException;
}
