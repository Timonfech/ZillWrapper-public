package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.apis.EntityEnricher;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EntityUpdateParserRegistry {

    private final List<EntityEnricher> parsers;

    public EntityEnricher resolve(EnrichmentRequest ctx) {
        return parsers.stream()
                .filter(p -> p.supports(ctx))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "No parser found for entityType=" + ctx.entityType() +
                        ", brandId=" + ctx.brandId() +
                        ", productId=" + ctx.productId()
                ));
    }
}