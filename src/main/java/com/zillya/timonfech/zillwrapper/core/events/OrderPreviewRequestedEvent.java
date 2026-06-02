package com.zillya.timonfech.zillwrapper.core.events;

import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;
import java.time.Instant;

@Getter
public class OrderPreviewRequestedEvent extends ApplicationEvent {
    private final BigInteger operationId;
    private final Long chatId;
    private final Integer sourceMessageId;
    private final Long userId;
    private final String previewId;
    private final OrderPreviewPayload payload;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final TelegramInboundEvent sourceEvent;

    public OrderPreviewRequestedEvent(Object source,
                                      BigInteger operationId,
                                      Long chatId,
                                      Integer sourceMessageId,
                                      Long userId,
                                      String previewId,
                                      OrderPreviewPayload payload,
                                      Instant createdAt,
                                      Instant expiresAt,
                                      TelegramInboundEvent sourceEvent) {
        super(source);
        this.operationId = operationId;
        this.chatId = chatId;
        this.sourceMessageId = sourceMessageId;
        this.userId = userId;
        this.previewId = previewId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.sourceEvent = sourceEvent;
    }
}
