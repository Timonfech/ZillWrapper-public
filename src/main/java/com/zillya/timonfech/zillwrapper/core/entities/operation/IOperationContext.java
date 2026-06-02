package com.zillya.timonfech.zillwrapper.core.entities.operation;

import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public interface IOperationContext {
    Long getEntitySourceId();
    @Nullable
    BigInteger getOperationId();
    void setOperationId(BigInteger id);

    /**
     * Optional pointer to the currently executing/recovering stage execution.
     * Root operation id is always exposed via getOperationId().
     */
    default @Nullable BigInteger getStageExecutionId() { return null; }
    default void setStageExecutionId(BigInteger id) {}

    Long getEntityId();
    IEntityWithStatus<?> getIEntityWithStatus();
    OperationType getOperationType();

    Long getInitiatorUserId();
    void setInitiatorUserId(Long id);
    InboundEvent<?> getSourceContext();

    default List<IArtifact> getArtifacts() { return new ArrayList<>(); }
    default void addArtifact(IArtifact artifact) {}
}
