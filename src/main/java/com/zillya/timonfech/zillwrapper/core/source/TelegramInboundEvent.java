package com.zillya.timonfech.zillwrapper.core.source;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEventStatus;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import lombok.Getter;
import lombok.Setter;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.util.UUID;

@Getter
public class TelegramInboundEvent implements InboundEvent<Update> {

    private final String id;
    private final SourceEntity sourceEntity;
    private final Update payload;
    private final Instant receivedAt;
    
    @Setter
    private InboundEventStatus status;

    public TelegramInboundEvent(SourceEntity sourceEntity, Update payload) {
        this.id = UUID.randomUUID().toString();
        this.sourceEntity = sourceEntity;
        this.payload = payload;
        this.receivedAt = Instant.now();
        this.status = InboundEventStatus.RECEIVED;
    }
}
