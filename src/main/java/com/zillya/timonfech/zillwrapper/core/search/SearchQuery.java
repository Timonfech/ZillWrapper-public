package com.zillya.timonfech.zillwrapper.core.search;

import lombok.Builder;

@Builder
public record SearchQuery(
        SearchEntityType entityType,
        Long orderId,
        Long woid,
        Long wzid,
        Long wid2,
        Long pid,
        Long lex,
        Long kid,
        String productName,
        String kon,
        String kof,
        String comment
) {
}
