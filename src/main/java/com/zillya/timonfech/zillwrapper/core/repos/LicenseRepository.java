package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<LicenseEntity, Long> {

    @EntityGraph(attributePaths = {"key"})
    Optional<LicenseEntity> findByExternalId(Long externalId);

    @EntityGraph(attributePaths = {"key"})
    Optional<LicenseEntity> findById(Long id);

    @EntityGraph(attributePaths = {"key"})
    Optional<LicenseEntity> findFirstByKey_OnlineKey(String onlineKey);

    @EntityGraph(attributePaths = {"key"})
    @Query("""
            select l from LicenseEntity l
            where lower(coalesce(l.key.onlineKey, '')) = lower(:onlineKey)
            """)
    Optional<LicenseEntity> findFirstByKey_OnlineKeyIgnoreCase(@Param("onlineKey") String onlineKey);

    @EntityGraph(attributePaths = {"key"})
    Optional<LicenseEntity> findFirstByKey_OfflineKey(String offlineKey);

    @EntityGraph(attributePaths = {"key"})
    @Query("""
            select l from LicenseEntity l
            where lower(coalesce(l.key.offlineKey, '')) = lower(:offlineKey)
            """)
    Optional<LicenseEntity> findFirstByKey_OfflineKeyIgnoreCase(@Param("offlineKey") String offlineKey);

    @EntityGraph(attributePaths = {"key"})
    Optional<LicenseEntity> findByKey_Id(Long keyId);

    @EntityGraph(attributePaths = {"key"})
    @Query("""
            select l from LicenseEntity l
            where lower(coalesce(l.key.onlineKey, '')) like lower(concat('%', :token, '%'))
               or lower(coalesce(l.key.offlineKey, '')) like lower(concat('%', :token, '%'))
            """)
    List<LicenseEntity> findAllByOnlineOrOfflineContainsCi(@Param("token") String token);

    @EntityGraph(attributePaths = {"key"})
    List<LicenseEntity> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"key"})
    List<LicenseEntity> findByOrderItemId(Long orderItemId);

    @Query("select max(l.externalId) from LicenseEntity l where l.brandId = :brandId and l.productId = :productId and l.externalId is not null")
    Optional<Long> findMaxExternalIdByProduct(@Param("brandId") Integer brandId, @Param("productId") Integer productId);

    @Query("select l.key.id from LicenseEntity l where l.id = :licenseId and l.key is not null")
    Optional<Long> findKeyIdByLicenseId(@Param("licenseId") Long licenseId);

    @Query("select l.productId from LicenseEntity l where l.id = :licenseId")
    Optional<Integer> findProductIdByLicenseId(@Param("licenseId") Long licenseId);
}
