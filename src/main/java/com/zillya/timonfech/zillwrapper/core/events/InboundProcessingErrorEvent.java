package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;

@Getter
public class InboundProcessingErrorEvent extends ApplicationEvent {
    private final String inboundEventId;
    private final SourceType sourceType;
    private final Long sourceId;
    private final Long chatId;
    private final Long userId;
    private final BigInteger parentOperationId;
    private final InboundErrorCategory category;
    private final String safeMessage;
    private final String internalMessage;
    private final Throwable cause;

    public InboundProcessingErrorEvent(Object source,
                                       String inboundEventId,
                                       SourceType sourceType,
                                       Long sourceId,
                                       Long chatId,
                                       Long userId,
                                       BigInteger parentOperationId,
                                       InboundErrorCategory category,
                                       String safeMessage,
                                       String internalMessage,
                                       Throwable cause) {
        super(source);
        this.inboundEventId = inboundEventId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.chatId = chatId;
        this.userId = userId;
        this.parentOperationId = parentOperationId;
        this.category = category;
        this.safeMessage = safeMessage;
        this.internalMessage = internalMessage;
        this.cause = cause;
    }
}
