package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;

import java.util.Optional;

/**
 * Strategy interface for generating licenses for a specific product item.
 */
public interface LicenseGenerator {
    /**
     * @return true if this generator can handle the given product.
     */
    boolean supports(ProductInfo product);

    /**
     * Executes license generation (e.g. API call).
     * Should update the item's processing status in the DB.
     */
    void generate(OrderItemEntity item, ProductInfo product);

    default void generate(OrderItemEntity item, ProductInfo product, Long originSourceId) {
        generate(item, product);
    }

    default Optional<SourceType> sourceType() {
        return Optional.empty();
    }
}
