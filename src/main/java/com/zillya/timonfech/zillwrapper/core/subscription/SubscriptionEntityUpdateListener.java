package com.zillya.timonfech.zillwrapper.core.subscription;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionEntityUpdateListener {

    private final LicenseSubscriptionRepository subscriptionRepository;

    @EventListener
    @Transactional
    public void onEntityUpdated(EntityUpdatedEvent event) {
        if (event.getEntityTypeEnum() == EntityTypeEnum.LICENSE) {
            handleLicenseUpdate(event);
            return;
        }
        if (event.getEntityTypeEnum() == EntityTypeEnum.ORDER) {
            handleOrderUpdate(event);
        }
    }

    private void handleLicenseUpdate(EntityUpdatedEvent event) {
        Long licenseId = event.getEntityId();
        if (licenseId == null) {
            return;
        }
        subscriptionRepository.findByLicenseId(licenseId).ifPresent(subscription -> {
            subscription.setUpdatedAt(Instant.now());
            subscriptionRepository.save(subscription);
        });
    }

    private void handleOrderUpdate(EntityUpdatedEvent event) {
        Long orderId = event.getEntityId();
        if (orderId == null) {
            return;
        }
        List<LicenseSubscriptionEntity> subs = subscriptionRepository.findByOrderId(orderId);
        if (subs.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (LicenseSubscriptionEntity sub : subs) {
            sub.setUpdatedAt(now);
        }
        subscriptionRepository.saveAll(subs);
    }
}
