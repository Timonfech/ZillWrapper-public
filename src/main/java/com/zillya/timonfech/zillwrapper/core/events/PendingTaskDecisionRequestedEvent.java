package com.zillya.timonfech.zillwrapper.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class PendingTaskDecisionRequestedEvent extends ApplicationEvent {

    public enum Decision {
        CONFIRM,
        CANCEL,
        EXPIRE,
        WA_CREATE_YES,
        WA_CREATE_NO,
        WA_CREATE_AND_CONFIRM,
        WA_SKIP_AND_CONFIRM
    }

    private final String taskId;
    private final Decision decision;
    private final Long actorUserId;
    private final String sourceActorId;
    private final Long chatId;
    private final Integer messageId;
    private final Instant decidedAt;

    public PendingTaskDecisionRequestedEvent(Object source,
                                             String taskId,
                                             Decision decision,
                                             Long actorUserId,
                                             String sourceActorId,
                                             Long chatId,
                                             Integer messageId,
                                             Instant decidedAt) {
        super(source);
        this.taskId = taskId;
        this.decision = decision;
        this.actorUserId = actorUserId;
        this.sourceActorId = sourceActorId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.decidedAt = decidedAt;
    }
}
