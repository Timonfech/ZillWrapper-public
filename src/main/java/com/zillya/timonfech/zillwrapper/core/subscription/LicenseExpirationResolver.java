package com.zillya.timonfech.zillwrapper.core.subscription;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class LicenseExpirationResolver {

    public Instant resolveOfflineExpectedExpiration(LicenseEntity license) {
        if (license == null) {
            return null;
        }
        Instant base = license.getCreatedAtOrigin();
        if (base == null) {
            base = license.getCreatedAt();
        }
        if (base == null || license.getPeriodAmount() == null || license.getPeriodUnit() == null) {
            return null;
        }

        return switch (license.getPeriodUnit()) {
            case DAY -> base.plus(license.getPeriodAmount(), ChronoUnit.DAYS);
            case MONTH -> base.plus(license.getPeriodAmount() * 30L, ChronoUnit.DAYS);
            case YEAR -> base.plus(license.getPeriodAmount() * 365L, ChronoUnit.DAYS);
        };
    }
}

