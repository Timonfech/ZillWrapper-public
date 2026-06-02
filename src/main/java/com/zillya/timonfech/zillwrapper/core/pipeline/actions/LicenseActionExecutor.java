package com.zillya.timonfech.zillwrapper.core.pipeline.actions;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;

public interface LicenseActionExecutor {
    boolean supports(LicenseEntity license);
    LicenseBlockOutcome updateStatus(LicenseEntity license, LicenseStatus targetStatus);
}
