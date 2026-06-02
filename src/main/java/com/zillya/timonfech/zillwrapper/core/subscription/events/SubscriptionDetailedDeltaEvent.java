package com.zillya.timonfech.zillwrapper.core.subscription.events;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

public class SubscriptionDetailedDeltaEvent extends ApplicationEvent {
    private final Long sourceId;
    private final Long orderId;
    private final Long licenseId;
    private final String keyPrefix;
    private final Instant changedAt;
    private final List<FieldDelta> deltas;

    public SubscriptionDetailedDeltaEvent(Object source,
                                          Long sourceId,
                                          Long orderId,
                                          Long licenseId,
                                          String keyPrefix,
                                          Instant changedAt,
                                          List<FieldDelta> deltas) {
        super(source);
        this.sourceId = sourceId;
        this.orderId = orderId;
        this.licenseId = licenseId;
        this.keyPrefix = keyPrefix;
        this.changedAt = changedAt;
        this.deltas = deltas == null ? List.of() : List.copyOf(deltas);
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public List<FieldDelta> getDeltas() {
        return deltas;
    }

    public record FieldDelta(String field, String before, String after) {
    }
}
