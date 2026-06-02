package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.apis.enrichers.OrderEnrichmentMode;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOperationOrchestratorRouter {

    private final SourceRepository sourceRepository;
    private final List<SourceOrderOperationOrchestrator> orchestrators;

    @EventListener
    public void onEntityUpdated(EntityUpdatedEvent event) {
        if (event.getEntityTypeEnum() != EntityTypeEnum.ORDER) {
            return;
        }
        if (event.getType() != OperationType.ENTITY_ENRICHMENT) {
            return;
        }
        if (event.getSourceId() == null) {
            return;
        }
        if (event.getOrderEnrichmentMode() != OrderEnrichmentMode.PAYED_SNAPSHOT) {
            return;
        }
        SourceEntity source = sourceRepository.findById(event.getSourceId()).orElse(null);
        if (source == null) {
            return;
        }
        for (SourceOrderOperationOrchestrator orchestrator : orchestrators) {
            if (!orchestrator.supports(event, source)) {
                continue;
            }
            orchestrator.start(event, source);
            return;
        }
        log.debug("No source order orchestrator for sourceId={} sourceType={} orderId={}",
                source.getId(),
                source.getType(),
                event.getEntityId());
    }
}
