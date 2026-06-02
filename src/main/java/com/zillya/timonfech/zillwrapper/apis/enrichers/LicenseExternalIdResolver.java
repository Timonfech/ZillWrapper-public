package com.zillya.timonfech.zillwrapper.apis.enrichers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;
import java.util.stream.LongStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseExternalIdResolver implements ExternalIdResolver {
    
    @Override
    public LongStream resolve(EnrichmentRequest ctx, LongSupplier latestIdSupplier) {
        if (ctx.entityId() != null) {
            // entityId in enrichment request is treated as external license id (user-facing id).
            return LongStream.of(ctx.entityId());
        }

        long from = ctx.from() == -1 ? latestIdSupplier.getAsLong() : ctx.from();
        long to = ctx.to();
        if (from == -1) {
            return LongStream.empty();
        }
        if (from < to) {
            log.warn("wa_catchup_resolver_invalid_range from={} to={} sourceId={} brandId={} productId={}",
                    from, to, ctx.sourceId(), ctx.brandId(), ctx.productId());
            return LongStream.empty();
        }
        log.debug("wa_catchup_resolver_from_ctx from={} to={} sourceId={} brandId={} productId={}",
                from, to, ctx.sourceId(), ctx.brandId(), ctx.productId());
        return LongStream.iterate(from, i -> i >= to, i -> i - 1);
    }
}
