package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OperationCompletedEvent extends ApplicationEvent {
    private final OperationExecutionEntity rootOperation;

    public OperationCompletedEvent(Object source, OperationExecutionEntity rootOperation) {
        super(source);
        this.rootOperation = rootOperation;
    }
}
