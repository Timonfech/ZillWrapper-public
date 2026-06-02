package com.zillya.timonfech.zillwrapper.core.interfaces;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;

import java.util.concurrent.CompletionStage;

public interface AsyncOperationHandler<T> extends OperationHandler<T> {

    CompletionStage<OperationResult<?>> handleAsync(T t) throws OperationCancelledException;
}

