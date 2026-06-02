package com.zillya.timonfech.zillwrapper.core.pipeline.resend;

import com.zillya.timonfech.zillwrapper.core.search.SearchEntityType;

public record ResendTarget(
        SearchEntityType entityType,
        Long orderId,
        Long orderItemId,
        Long licenseId
) {
    public String encode() {
        return entityType.name() + ":" + n(orderId) + ":" + n(orderItemId) + ":" + n(licenseId);
    }

    public static ResendTarget decode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty resend target");
        }
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid resend target: " + raw);
        }
        return new ResendTarget(
                SearchEntityType.valueOf(parts[0]),
                p(parts[1]),
                p(parts[2]),
                p(parts[3])
        );
    }

    private static String n(Long value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static Long p(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        return Long.parseLong(value);
    }
}
