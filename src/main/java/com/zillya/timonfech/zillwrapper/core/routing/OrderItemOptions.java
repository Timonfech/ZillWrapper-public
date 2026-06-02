package com.zillya.timonfech.zillwrapper.core.routing;

public record OrderItemOptions(
        Integer serverNumber,
        Boolean subscriptionDetailed,
        Boolean notifyClient,
        String warningLeadRaw,
        Integer subscriptionIntervalMinutes,
        Boolean subscribeExplicit
) {

    public static OrderItemOptions empty() {
        return new OrderItemOptions(null, null, null, null, null, null);
    }
}
