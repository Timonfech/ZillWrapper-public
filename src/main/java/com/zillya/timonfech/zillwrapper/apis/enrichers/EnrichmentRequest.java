package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public record EnrichmentRequest(
        UUID taskId,
        Long sourceId,
        EntityTypeEnum entityType,
        Integer brandId,
        Integer productId,
        @Nullable OrderEnrichmentMode ordersMode,
        /**
         * External entity id from outer source (user-facing id).
         * For example:
         * - License enrichment: external license id
         * - Order enrichment: whiteAdmin order id
         * Must be null for range mode (from/to).
         */
        @Nullable Long entityId,
        long from, // -1 means to search from the newest id available
        long to,   // could be 0, means to the first available
        @Nullable LicenseEnrichmentScope licenseScope,
        List<EnrichmentProductCandidate> productCandidates
) {
    public EnrichmentRequest {
        if (licenseScope == null) {
            licenseScope = LicenseEnrichmentScope.SINGLE_PRODUCT;
        }
        if (productCandidates == null) {
            productCandidates = List.of();
        } else {
            productCandidates = List.copyOf(productCandidates);
        }
    }

    public EnrichmentRequest(
            UUID taskId,
            Long sourceId,
            EntityTypeEnum entityType,
            Integer brandId,
            Integer productId,
            @Nullable OrderEnrichmentMode ordersMode,
            @Nullable Long entityId,
            long from,
            long to
    ) {
        this(taskId, sourceId, entityType, brandId, productId, ordersMode, entityId, from, to,
                LicenseEnrichmentScope.SINGLE_PRODUCT, List.of());
    }
}
