package com.zillya.timonfech.zillwrapper.core.subscription.warnings;

import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity.DeliveryStatus;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseSubscriptionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.SubscriptionWarningDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionWarningDeliveryDispatcher {

    private final SubscriptionWarningDeliveryRepository deliveryRepository;
    private final LicenseSubscriptionRepository subscriptionRepository;
    private final SubscriptionWarningSourceRouter sourceRouter;
    @Value("${subscription.delivery.max-attempts:3}")
    private int maxAttempts;
    @Value("${subscription.delivery.backoff.attempt1-minutes:1}")
    private int backoffAttempt1Minutes;
    @Value("${subscription.delivery.backoff.attempt2-minutes:5}")
    private int backoffAttempt2Minutes;
    @Value("${subscription.delivery.backoff.default-minutes:30}")
    private int backoffDefaultMinutes;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${subscription.delivery.fixed-delay-ms:60000}")
    @Transactional
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int normalized = deliveryRepository.initializeNullVersions();
            if (normalized > 0) {
                log.debug("Normalized legacy subscription deliveries with null version: updatedRows={}", normalized);
            }
            Instant now = Instant.now();
            List<SubscriptionWarningDeliveryEntity> due = deliveryRepository.findByStatusInAndNextAttemptAtBefore(
                    List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED, DeliveryStatus.DEFERRED_NO_EXTERNAL_ID),
                    now
            );
            log.debug("Subscription delivery tick: now={} dueDeliveries={}", now, due.size());
            for (SubscriptionWarningDeliveryEntity delivery : due) {
                processDelivery(delivery, now);
            }
        } finally {
            running.set(false);
        }
    }

    private void processDelivery(SubscriptionWarningDeliveryEntity delivery, Instant now) {
        log.debug("Delivery process start: deliveryId={} subId={} licenseId={} orderId={} sourceId={} status={} attempts={} nextAttemptAt={}",
                delivery.getId(),
                delivery.getSubscriptionId(),
                delivery.getLicenseId(),
                delivery.getOrderId(),
                delivery.getSourceId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt());
        WarningDeliveryResult result = sourceRouter.route(delivery);
        log.debug("Delivery route result: deliveryId={} success={} retryable={} message={}",
                delivery.getId(), result.success(), result.retryable(), result.message());
        if (result.success()) {
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setSentAt(now);
            delivery.setLastError(null);
            deliveryRepository.save(delivery);
            subscriptionRepository.findById(delivery.getSubscriptionId()).ifPresent(sub -> {
                sub.setStatus(LicenseSubscriptionEntity.SubscriptionStatus.WARNING_SENT);
                sub.setNotifiedAt(now);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);
            });
            log.debug("Subscription warning delivered. deliveryId={} subscriptionId={} licenseId={}",
                    delivery.getId(), delivery.getSubscriptionId(), delivery.getLicenseId());
            return;
        }

        int attempts = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempts);
        delivery.setLastError(result.message());

        if (!result.retryable() || attempts >= Math.max(1, maxAttempts)) {
            delivery.setStatus(DeliveryStatus.GAVE_UP);
            delivery.setNextAttemptAt(now.plus(24, ChronoUnit.HOURS));
            log.warn("Subscription warning give-up. deliveryId={} attempts={} reason={}",
                    delivery.getId(), attempts, result.message());
        } else {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setNextAttemptAt(now.plus(backoffMinutes(attempts), ChronoUnit.MINUTES));
            log.warn("Subscription warning retry scheduled. deliveryId={} attempts={} next={} reason={}",
                    delivery.getId(), attempts, delivery.getNextAttemptAt(), result.message());
        }
        deliveryRepository.save(delivery);
    }

    private long backoffMinutes(int attempt) {
        return switch (attempt) {
            case 1 -> Math.max(1, backoffAttempt1Minutes);
            case 2 -> Math.max(1, backoffAttempt2Minutes);
            default -> Math.max(1, backoffDefaultMinutes);
        };
    }
}
