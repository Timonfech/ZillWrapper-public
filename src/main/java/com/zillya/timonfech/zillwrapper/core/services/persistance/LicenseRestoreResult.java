package com.zillya.timonfech.zillwrapper.core.services.persistance;

import java.util.List;

public record LicenseRestoreResult(
        boolean applied,
        Long licenseId,
        Long fromVersion,
        Long targetVersion,
        List<FieldDiff> diff,
        List<String> warnings
) {
    public record FieldDiff(String field, String currentValue, String targetValue) {}
}

