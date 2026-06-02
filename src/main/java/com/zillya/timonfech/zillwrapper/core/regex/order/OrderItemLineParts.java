package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;

public record OrderItemLineParts(ProductInfo product, String productText, String specText) {
}
