package com.zillya.timonfech.zillwrapper.core.interactions;

import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class HandlingInfoEvent extends ApplicationEvent{
    private final OperationExecutionEntity execution;

    public HandlingInfoEvent(Object source, OperationExecutionEntity execution) {
        super(source);
        this.execution = execution;
    }
}