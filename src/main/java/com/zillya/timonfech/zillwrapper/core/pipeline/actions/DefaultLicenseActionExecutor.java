package com.zillya.timonfech.zillwrapper.core.pipeline.actions;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(1000)
public class DefaultLicenseActionExecutor implements LicenseActionExecutor {
    @Override
    public boolean supports(LicenseEntity license) {
        return true;
    }

    @Override
    public LicenseBlockOutcome updateStatus(LicenseEntity license, LicenseStatus targetStatus) {
        if (targetStatus != LicenseStatus.ALLOWED && targetStatus != LicenseStatus.BLOCKED) {
            return LicenseBlockOutcome.failed("Unsupported target status: " + targetStatus);
        }
        return LicenseBlockOutcome.failed("No source-specific status executor for license #" + license.getId());
    }
}
