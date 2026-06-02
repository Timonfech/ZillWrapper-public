package com.zillya.timonfech.zillwrapper.core.services.persistance;

public record LicenseRestoreRequest(
        Long licenseId,
        Long targetVersionNo,
        String actor,
        boolean dryRun
) {}

