package com.zillya.timonfech.zillwrapper.apis;

import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;

/**
 * Legacy sync/update handler contract kept for backward compatibility.
 */
public interface EntityUpdate {
    IEntityWithStatus<?> targetType();
    String name();
    boolean supports(Object event);
    OperationResult<?> handle(Object event) throws OperationCancelledException;
}
