package com.zillya.timonfech.zillwrapper.core.entities.source;

import java.time.Instant;

/**
 * Generic interface for all inbound events from various sources.
 * @param <P> The type of the raw payload (e.g. Update for Telegram, HttpRequest for API)
 */
public interface InboundEvent<P> {
    String getId();
    SourceEntity getSourceEntity();
    P getPayload();
    Instant getReceivedAt();
    InboundEventStatus getStatus();
    void setStatus(InboundEventStatus status);
}