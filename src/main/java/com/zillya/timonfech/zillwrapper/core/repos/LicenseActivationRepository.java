package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseActivationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseActivationRepository extends JpaRepository<LicenseActivationEntity, Long> {
    void deleteByDinoKey_Id(Long keyId);
}
