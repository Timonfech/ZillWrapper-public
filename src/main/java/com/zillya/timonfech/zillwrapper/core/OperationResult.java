package com.zillya.timonfech.zillwrapper.core;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OperationResult<T> {
    private final boolean success;
    private final String errorMessage;
    private final T payload;
    private final boolean recoverable;

    public OperationResult(boolean success, String errorMessage, T payload, boolean recoverable) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.payload = payload;
        this.recoverable = recoverable;
    }

    public static <T> OperationResult<T> ok(T payload) {
        return new OperationResult<>(true, null, payload, false);
    }

    public static <T> OperationResult<T> fail(String error, boolean recoverable) {
        return new OperationResult<>(false, error, null, recoverable);
    }
}
