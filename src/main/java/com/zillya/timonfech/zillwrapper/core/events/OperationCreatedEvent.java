package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;

@Getter
public class OperationCreatedEvent extends ApplicationEvent {
    private final BigInteger operationId;
    private final InboundEvent<?> sourceContext;

    public OperationCreatedEvent(Object source, BigInteger operationId, InboundEvent<?> sourceContext) {
        super(source);
        this.operationId = operationId;
        this.sourceContext = sourceContext;
    }
}
