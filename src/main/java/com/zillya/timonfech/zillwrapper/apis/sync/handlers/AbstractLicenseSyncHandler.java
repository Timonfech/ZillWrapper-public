package com.zillya.timonfech.zillwrapper.apis.sync.handlers;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.sync.EntitySyncHandler;
import com.zillya.timonfech.zillwrapper.apis.sync.SyncRequest;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public abstract class AbstractLicenseSyncHandler implements EntitySyncHandler {
    protected final LicenseRepository licenseRepository;
    protected final AbstractWhiteAdminClient client;

    protected abstract Integer brandId();
    protected abstract Integer productId();

    @Override
    public boolean supports(SyncRequest request) {
        return request.entityType() == EntityTypeEnum.LICENSE
                && Objects.equals(request.brandId(), brandId())
                && Objects.equals(request.productId(), productId());
    }

    @Override
    public void sync(SyncRequest request) {
        LicenseEntity license = licenseRepository.findById(request.entityId())
                .orElseThrow(() -> new EntityNotFoundException("License not found with id: " + request.entityId()));
        doSync(license);
    }

    protected abstract void doSync(LicenseEntity license);
}
