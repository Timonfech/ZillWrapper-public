package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;

import java.util.List;

/**
 * Intermediate data structure representing a single item parsed from an order request.
 * Used to transport data from the Router to the OrderCreationHandler.
 */
public record OrderItemSpec(
        ProductInfo product,
        int count,
        BusinessPeriod period,
        int computers,
        List<OutputType> outputTypes,
        List<KeyType> keyTypes,
        boolean subscribed,
        OrderItemOptions options
) {
    public OrderItemSpec(
            ProductInfo product,
            int count,
            BusinessPeriod period,
            int computers,
            List<OutputType> outputTypes
    ) {
        this(product, count, period, computers, outputTypes, List.of(KeyType.ONLINE), false, OrderItemOptions.empty());
    }
}
