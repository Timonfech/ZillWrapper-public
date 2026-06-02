package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LicenseSubscriptionRepository extends JpaRepository<LicenseSubscriptionEntity, Long> {

    Optional<LicenseSubscriptionEntity> findByLicenseId(Long licenseId);

    List<LicenseSubscriptionEntity> findByOrderId(Long orderId);

    List<LicenseSubscriptionEntity> findByStatusInAndNextCheckAtBefore(List<SubscriptionStatus> statuses, Instant now);

    List<LicenseSubscriptionEntity> findTop50ByStatusInOrderByNextCheckAtAsc(List<SubscriptionStatus> statuses);

    List<LicenseSubscriptionEntity> findTop200ByStatusOrderByIdAsc(SubscriptionStatus status);
}
