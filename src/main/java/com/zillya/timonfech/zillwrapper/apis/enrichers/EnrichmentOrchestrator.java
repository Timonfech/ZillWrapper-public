package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.apis.EntityEnricher;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class EnrichmentOrchestrator {

    private final EntityUpdateParserRegistry registry;

    public OperationResult<?> handle(EnrichmentRequest ctx, AtomicBoolean isCancelled) throws OperationCancelledException {
        EntityEnricher parser = registry.resolve(ctx);
        return parser.handle(ctx, isCancelled);
    }
}