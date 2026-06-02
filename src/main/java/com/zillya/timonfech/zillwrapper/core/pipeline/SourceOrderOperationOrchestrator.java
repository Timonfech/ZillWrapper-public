package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;

public interface SourceOrderOperationOrchestrator {
    boolean supports(EntityUpdatedEvent event, SourceEntity source);
    void start(EntityUpdatedEvent event, SourceEntity source);
}

