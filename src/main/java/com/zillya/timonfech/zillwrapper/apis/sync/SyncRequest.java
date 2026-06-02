package com.zillya.timonfech.zillwrapper.apis.sync;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;

import java.math.BigInteger;

public record SyncRequest(
        EntityTypeEnum entityType,
        Long entityId,
        Integer brandId,
        Integer productId,
        Long sourceId
) implements IOperationContext {

    @Override
    public BigInteger getOperationId() {
        return null;
    }

    @Override
    public void setOperationId(BigInteger id) {
        // immutable request, no-op
    }

    @Override
    public IEntityWithStatus<?> getIEntityWithStatus() {
        return new IEntityWithStatus<OperationStatus>() {
            @Override
            public EntityTypeEnum getEntityType() {
                return entityType;
            }

            @Override
            public OperationStatus getStatus() {
                return OperationStatus.RUNNING;
            }
        };
    }

    @Override
    public Long getEntitySourceId() {
        return sourceId;
    }

    @Override
    public Long getEntityId() {
        return entityId;
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
        // immutable request, no-op
    }

    @Override
    public com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent<?> getSourceContext() {
        return null;
    }
}
