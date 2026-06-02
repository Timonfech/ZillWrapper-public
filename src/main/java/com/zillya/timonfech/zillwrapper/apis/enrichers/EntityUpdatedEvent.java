package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

public class EntityUpdatedEvent extends ApplicationEvent {
    private final Long sourceId;
    private final OperationType type;
    private final EntityTypeEnum entityTypeEnum;
    private final Long entityId;
    private final Instant when;
    private final OrderEnrichmentMode orderEnrichmentMode;

    public EntityUpdatedEvent(Object source, Long sourceId, OperationType type, EntityTypeEnum entityTypeEnum, Long entityId, Instant when) {
        this(source, sourceId, type, entityTypeEnum, entityId, when, null);
    }

    public EntityUpdatedEvent(
            Object source,
            Long sourceId,
            OperationType type,
            EntityTypeEnum entityTypeEnum,
            Long entityId,
            Instant when,
            OrderEnrichmentMode orderEnrichmentMode
    ) {
        super(source);
        this.sourceId = sourceId;
        this.type = type;
        this.entityTypeEnum = entityTypeEnum;
        this.entityId = entityId;
        this.when = when;
        this.orderEnrichmentMode = orderEnrichmentMode;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public OperationType getType() {
        return type;
    }

    public EntityTypeEnum getEntityTypeEnum() {
        return entityTypeEnum;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Instant getWhen() {
        return when;
    }

    public OrderEnrichmentMode getOrderEnrichmentMode() {
        return orderEnrichmentMode;
    }
}
