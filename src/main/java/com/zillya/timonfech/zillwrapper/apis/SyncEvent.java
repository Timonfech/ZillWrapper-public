package com.zillya.timonfech.zillwrapper.apis;

import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
//*
// used to sync updated entity with outer services
//
//*/
public record SyncEvent(
        Long sourceId,
        //FROM WHERE IS THIS EVENT, DOES NOT TELL YOU WHAT OUTER SOURCE YOU SHOULD USE
        BigInteger operationId,
        Long entityId,
        IEntityWithStatus<?> iEntityType

) implements IOperationContext {

    @Override
    public Long getEntitySourceId() {
        return this.sourceId;
    }

    @Nullable
    @Override
    public BigInteger getOperationId() {
        return operationId;
    }

    @Override
    public Long getEntityId() {
        return this.entityId;
    }

    @Override
    public IEntityWithStatus<?> getIEntityWithStatus() {
        return iEntityType;
    }

    @Override
    public void setOperationId(BigInteger id) {
        // immutable event, no-op
    }

    @Override
    public OperationType getOperationType() {
        return OperationType.ENTITY_SYNC;
    }

    @Override
    public Long getInitiatorUserId() {
        return null;
    }

    @Override
    public void setInitiatorUserId(Long id) {
        // immutable event, no-op
    }

    @Override
    public com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent<?> getSourceContext() {
        return null;
    }
}
