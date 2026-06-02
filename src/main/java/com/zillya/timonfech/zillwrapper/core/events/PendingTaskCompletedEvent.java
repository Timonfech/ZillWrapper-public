package com.zillya.timonfech.zillwrapper.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;

@Getter
public class PendingTaskCompletedEvent extends ApplicationEvent {
    private final String taskId;
    private final BigInteger operationId;

    public PendingTaskCompletedEvent(Object source, String taskId, BigInteger operationId) {
        super(source);
        this.taskId = taskId;
        this.operationId = operationId;
    }
}
