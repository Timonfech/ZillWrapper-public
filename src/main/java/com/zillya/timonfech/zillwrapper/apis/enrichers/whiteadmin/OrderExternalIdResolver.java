package com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin;

import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.apis.enrichers.ExternalIdResolver;
import com.zillya.timonfech.zillwrapper.apis.enrichers.OrderEnrichmentMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;

@Component
@RequiredArgsConstructor
public class OrderExternalIdResolver implements ExternalIdResolver {
    private final WhiteAdminPayedOrderIdsProvider payedOrderIdsProvider;

    @Override
    public LongStream resolve(EnrichmentRequest ctx, LongSupplier latestIdSupplier) {
        if (ctx.ordersMode() == OrderEnrichmentMode.PAYED_SNAPSHOT) {
            List<Long> payedIds = payedOrderIdsProvider.getPayedIds();
            if (!payedIds.isEmpty() && payedIds.getFirst() != -1L) {
                return payedIds.stream().mapToLong(Long::longValue);
            }
            return LongStream.empty();
        }

        if (ctx.entityId() != null) {
            // entityId in enrichment request is an external order id (white admin id).
            return LongStream.of(ctx.entityId());
        }

        return LongStream.iterate(latestIdSupplier.getAsLong(), i -> i >= ctx.to(), i -> i - 1);
    }

}
