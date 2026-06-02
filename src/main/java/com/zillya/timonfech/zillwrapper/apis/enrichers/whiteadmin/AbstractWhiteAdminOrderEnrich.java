package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.AbstractEntityEnrich;
import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.apis.enrichers.ExternalIdResolver;
import com.zillya.timonfech.zillwrapper.apis.enrichers.OrderDedupService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.OrderAggregate;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.OrderUpsertResult;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j

public abstract class AbstractWhiteAdminOrderEnrich extends AbstractEntityEnrich<OrderEntity> {
    private final AbstractWhiteAdminClient client;
    private final OrderExternalIdResolver externalIdResolver;
    private final OrderDedupService dedupService;

    public AbstractWhiteAdminOrderEnrich(
            ApplicationEventPublisher publisher,
            EnrichmentProgressRegistry progressRegistry,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
            AbstractWhiteAdminClient client,
            OrderExternalIdResolver externalIdResolver,
            OrderDedupService dedupService
    ) {
        super(publisher, progressRegistry, enrichmentTaskExecutor);
        this.client = client;
        this.externalIdResolver = externalIdResolver;
        this.dedupService = dedupService;
    }

    @Override
    public abstract long fetchLatestId();

    protected abstract List<Long> getPayedIds();

    @Override
    protected ExternalIdResolver resolver() {
        return externalIdResolver;
    }

    @Override
    public OrderEntity targetType() {
        return new OrderEntity();
    }

    protected abstract OrderAggregate parse(Document doc, Long externalId);

    protected abstract String targetUrl();
    @Override
    public boolean supports(EnrichmentRequest request) {
        return request.entityType() == EntityTypeEnum.ORDER;
    }

    @Override
    @Transactional
    protected boolean processSingle(EnrichmentRequest ctx, Long externalId) {
        try {
            Document doc = loadDocument(externalId);

            OrderAggregate aggregate;
            try {
                aggregate = parse(doc, externalId);
            } catch (RuntimeException parseEx) {
                log.warn("Skip WhiteAdmin order externalId={} due to parse failure: {}",
                        externalId, parseEx.getMessage());
                return false;
            }
            OrderUpsertResult result = dedupService.upsert(ctx, aggregate);
            if (result.orderChanged()) {
                publishEntityUpdated(
                        ctx.sourceId(),
                        EntityTypeEnum.ORDER,
                        result.orderId(),
                        ctx.ordersMode()
                );
            }

            if (result.clientChanged() && result.clientId() != null) {
                publishEntityUpdated(
                        ctx.sourceId(),
                        EntityTypeEnum.CLIENT,
                        result.clientId()
                );
            }
            return result.stopScanning();
        } catch (IOException e) {
            log.error("Failed to load WhiteAdmin document for id={}", externalId, e);
            return false;
        }
    }

    protected Document loadDocument(Long externalId) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(externalId)));
        return client.loadDocument(targetUrl(), params, false);
    }

    @Override
    public String name() {
        return "ORDER_ENRICHER";
    }

}
