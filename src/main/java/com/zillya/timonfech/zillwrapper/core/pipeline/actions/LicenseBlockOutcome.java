package com.zillya.timonfech.zillwrapper.core.pipeline.actions;

public record LicenseBlockOutcome(
        boolean externalApplied,
        boolean internalApplied,
        String warning,
        String error
) {
    public static LicenseBlockOutcome externalAndInternal() {
        return new LicenseBlockOutcome(true, true, null, null);
    }

    public static LicenseBlockOutcome internalOnly(String warning) {
        return new LicenseBlockOutcome(false, true, warning, null);
    }

    public static LicenseBlockOutcome failed(String error) {
        return new LicenseBlockOutcome(false, false, null, error);
    }
}

