package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseVersionRepository extends JpaRepository<LicenseVersionEntity, Long> {
    Optional<LicenseVersionEntity> findByLicenseIdAndVersionNo(Long licenseId, Long versionNo);
}

