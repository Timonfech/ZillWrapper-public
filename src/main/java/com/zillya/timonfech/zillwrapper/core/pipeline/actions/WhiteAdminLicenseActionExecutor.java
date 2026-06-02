package com.zillya.timonfech.zillwrapper.core.pipeline.actions;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(20)
@RequiredArgsConstructor
public class WhiteAdminLicenseActionExecutor implements LicenseActionExecutor {
    private final WhiteAdminStatusUpdateService updateService;

    @Override
    public boolean supports(LicenseEntity license) {
        return license != null && license.getKey() instanceof WhiteAdminKeyEntity;
    }

    @Override
    public LicenseBlockOutcome updateStatus(LicenseEntity license, LicenseStatus targetStatus) {
        if (targetStatus != LicenseStatus.ALLOWED && targetStatus != LicenseStatus.BLOCKED) {
            return LicenseBlockOutcome.failed("Unsupported target status for WhiteAdmin: " + targetStatus);
        }
        boolean applied = updateService.updateStatus(license, targetStatus);
        if (!applied) {
            return LicenseBlockOutcome.failed("WhiteAdmin status update failed for license #" + license.getId());
        }
        return LicenseBlockOutcome.externalAndInternal();
    }
}
