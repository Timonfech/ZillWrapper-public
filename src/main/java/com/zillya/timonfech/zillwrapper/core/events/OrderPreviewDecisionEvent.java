package com.zillya.timonfech.zillwrapper.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class OrderPreviewDecisionEvent extends ApplicationEvent {
    public enum Decision {
        CONFIRM,
        CANCEL,
        EXPIRED
    }

    private final String previewId;
    private final Decision decision;
    private final Long chatId;
    private final Long userId;
    private final Instant decidedAt;

    public OrderPreviewDecisionEvent(Object source,
                                     String previewId,
                                     Decision decision,
                                     Long chatId,
                                     Long userId,
                                     Instant decidedAt) {
        super(source);
        this.previewId = previewId;
        this.decision = decision;
        this.chatId = chatId;
        this.userId = userId;
        this.decidedAt = decidedAt;
    }
}
