package com.zillya.timonfech.zillwrapper.apis.sync;

import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

public interface EntitySyncHandler {
    
    boolean supports(SyncRequest request);

    @OperationStep(type = OperationType.ENTITY_SYNC, stepProps = {OperationStep.Props.CRUCIAL, OperationStep.Props.FINAL})
    void sync(SyncRequest request);
}
