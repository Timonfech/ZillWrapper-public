package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminActivationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhiteAdminActivationRepository extends JpaRepository<WhiteAdminActivationEntity, Long> {
    void deleteByWhiteAdminKey_Id(Long keyId);
}
