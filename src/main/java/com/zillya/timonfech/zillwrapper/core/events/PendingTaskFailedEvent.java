package com.zillya.timonfech.zillwrapper.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PendingTaskFailedEvent extends ApplicationEvent {
    private final String taskId;
    private final String safeMessage;

    public PendingTaskFailedEvent(Object source, String taskId, String safeMessage) {
        super(source);
        this.taskId = taskId;
        this.safeMessage = safeMessage;
    }
}
