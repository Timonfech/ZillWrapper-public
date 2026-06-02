package com.zillya.timonfech.zillwrapper.core.regex.order;

public record ParsedOrderFlags(
        Boolean excel,
        Boolean subscribe,
        Boolean partner,
        Boolean text,
        Boolean detailed,
        Boolean notifyClient,
        String warningLeadRaw,
        Integer subscriptionIntervalMinutes
) {

    public static ParsedOrderFlags empty() {
        return new ParsedOrderFlags(null, null, null, null, null, null, null, null);
    }
}
