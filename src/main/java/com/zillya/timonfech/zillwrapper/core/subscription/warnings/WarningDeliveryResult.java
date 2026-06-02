package com.zillya.timonfech.zillwrapper.core.subscription.warnings;

public record WarningDeliveryResult(boolean success, boolean retryable, String message) {

    public static WarningDeliveryResult ok(String message) {
        return new WarningDeliveryResult(true, false, message);
    }

    public static WarningDeliveryResult retry(String message) {
        return new WarningDeliveryResult(false, true, message);
    }

    public static WarningDeliveryResult fail(String message) {
        return new WarningDeliveryResult(false, false, message);
    }
}
