package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;

import java.util.function.LongSupplier;
import java.util.stream.LongStream;

public interface ExternalIdResolver {
    LongStream resolve(EnrichmentRequest ctx, LongSupplier latestIdSupplier) throws OperationCancelledException;
}
