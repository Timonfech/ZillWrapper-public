package com.zillya.timonfech.zillwrapper.core.entities.source;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime-only inbound event used to resume pipeline steps without a transport payload.
 */
@Getter
public class SyntheticInboundEvent implements InboundEvent<Object> {
    private final String id;
    private final SourceEntity sourceEntity;
    private final Object payload;
    private final Instant receivedAt;
    @Setter
    private InboundEventStatus status;

    public SyntheticInboundEvent(SourceEntity sourceEntity) {
        this.id = UUID.randomUUID().toString();
        this.sourceEntity = sourceEntity;
        this.payload = null;
        this.receivedAt = Instant.now();
        this.status = InboundEventStatus.RECEIVED;
    }
}
