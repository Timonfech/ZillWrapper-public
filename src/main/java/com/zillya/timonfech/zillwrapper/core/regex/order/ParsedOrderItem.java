package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;

import java.util.List;

public record ParsedOrderItem(
        ProductInfo product,
        int count,
        BusinessPeriod period,
        int computers,
        List<OutputType> outputTypes,
        List<KeyType> keyTypes,
        boolean subscribed,
        OrderItemOptions options
) {
}
