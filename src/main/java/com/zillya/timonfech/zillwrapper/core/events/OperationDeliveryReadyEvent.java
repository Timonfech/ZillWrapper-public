package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;
import java.util.List;

@Getter
public class OperationDeliveryReadyEvent extends ApplicationEvent {
    private final BigInteger parentOperationId;
    private final BigInteger stageExecutionId;
    private final Long orderId;
    private final List<Long> successItemIds;
    private final List<IArtifact> artifacts;
    private final List<OrderDeliveryTargetEntity> deliveryTargets;
    private final Long initiatorUserId;
    private final InboundEvent<?> sourceContext;

    public OperationDeliveryReadyEvent(Object source,
                                       BigInteger parentOperationId,
                                       BigInteger stageExecutionId,
                                       Long orderId,
                                       List<Long> successItemIds,
                                       List<IArtifact> artifacts,
                                       List<OrderDeliveryTargetEntity> deliveryTargets,
                                       Long initiatorUserId,
                                       InboundEvent<?> sourceContext) {
        super(source);
        this.parentOperationId = parentOperationId;
        this.stageExecutionId = stageExecutionId;
        this.orderId = orderId;
        this.successItemIds = successItemIds;
        this.artifacts = artifacts;
        this.deliveryTargets = deliveryTargets;
        this.initiatorUserId = initiatorUserId;
        this.sourceContext = sourceContext;
    }
}
