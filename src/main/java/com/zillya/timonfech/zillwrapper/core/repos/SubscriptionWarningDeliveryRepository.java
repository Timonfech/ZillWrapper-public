package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionWarningDeliveryRepository extends JpaRepository<SubscriptionWarningDeliveryEntity, Long> {

    Optional<SubscriptionWarningDeliveryEntity> findBySubscriptionIdAndWindowAt(Long subscriptionId, Instant windowAt);

    List<SubscriptionWarningDeliveryEntity> findByStatusInAndNextAttemptAtBefore(List<DeliveryStatus> statuses, Instant now);

    @Modifying
    @Transactional
    @Query(value = "update subscription_warning_delivery set version = 0 where version is null", nativeQuery = true)
    int initializeNullVersions();
}
