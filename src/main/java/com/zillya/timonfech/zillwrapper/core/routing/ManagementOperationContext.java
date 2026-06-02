package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Context for operations triggered by commands (often via Reply).
 */
@Getter
@Setter
@RequiredArgsConstructor
public class ManagementOperationContext implements IOperationContext {

    private final Long sourceId;
    private final OperationType operationType;
    private final Integer replyToMessageId;
    private final String commandPayload;
    private final InboundEvent<?> sourceContext;

    private Long initiatorUserId;
    private BigInteger operationId;
    private BigInteger stageExecutionId;

    @Override
    public Long getEntitySourceId() {
        return sourceId;
    }

    @Override
    @Nullable
    public BigInteger getOperationId() {
        return operationId;
    }

    @Override
    public Long getEntityId() {
        return null;
    }

    @Override
    public IEntityWithStatus<?> getIEntityWithStatus() {
        return null;
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public Long getInitiatorUserId() {
        return initiatorUserId;
    }

    @Override
    public InboundEvent<?> getSourceContext() {
        return sourceContext;
    }
}
