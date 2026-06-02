package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.BaseKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.DinoKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LicenseManagementService {

    private final LicenseRepository licenseRepository;

    /**
     * Provisions a WhiteAdmin license using an externally generated key.
     *
     * @param orderId     ID of the associated order
     * @param clientId    ID of the associated client
     * @param period      Duration of the license validity
     * @param productId   ID of the product
     * @param brandId     ID of the brand
     * @param externalKey The fully populated WhiteAdminKeyEntity from the external source
     * @return The saved LicenseEntity
     */
    @Transactional
    public LicenseEntity provisionWhiteAdminLicense(
            Long orderId,
            Long clientId,
            BusinessPeriod period,
            Integer productId,
            Integer brandId,
            WhiteAdminKeyEntity externalKey) {

        return buildAndSaveLicense(orderId, clientId, period, productId, brandId, externalKey);
    }

    @Transactional
    public LicenseEntity provisionWhiteAdminLicense(
            Long orderId,
            Long orderItemId,
            Long clientId,
            Long sourceId,
            BusinessPeriod period,
            Integer productId,
            Integer brandId,
            WhiteAdminKeyEntity externalKey) {

        LicenseEntity saved = buildAndSaveLicense(orderId, clientId, period, productId, brandId, externalKey);
        saved.setOrderItemId(orderItemId);
        saved.setSourceId(sourceId);
        return licenseRepository.save(saved);
    }

    /**
     * Provisions a standard Dino license using an externally generated key.
     *
     * @param orderId     ID of the associated order
     * @param clientId    ID of the associated client
     * @param period      Duration of the license validity
     * @param productId   ID of the product
     * @param brandId     ID of the brand
     * @param externalKey The fully populated DinoKeyEntity from the external source
     * @return The saved LicenseEntity
     */
    @Transactional
    public LicenseEntity provisionDinoLicense(
            Long orderId,
            Long clientId,
            BusinessPeriod period,
            Integer productId,
            Integer brandId,
            DinoKeyEntity externalKey) {

        return buildAndSaveLicense(orderId, clientId, period, productId, brandId, externalKey);
    }

    /**
     * Core internal method to assemble and persist the license entity.
     * Handles date calculations and binds the external key to the license.
     */
    private LicenseEntity buildAndSaveLicense(
            Long orderId,
            Long clientId,
            BusinessPeriod period,
            Integer productId,
            Integer brandId,
            BaseKeyEntity key) {

        LicenseEntity license = new LicenseEntity();
        license.setOrderId(orderId);
        license.setClientId(clientId);
        license.setBusinessPeriod(period);
        license.setProductId(productId);
        license.setBrandId(brandId);

        license.setStatus(LicenseStatus.ALLOWED);

        Instant now = Instant.now();
        license.setCreatedAt(now);
        license.setCreatedAtOrigin(now);

        // Bind the externally provided key to the license.
        // Thanks to CascadeType.ALL on the LicenseEntity, the key will be saved automatically.
        license.setKey(key);

        return licenseRepository.save(license);
    }
}
