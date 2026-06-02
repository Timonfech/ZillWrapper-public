package com.zillya.timonfech.zillwrapper.core.subscription;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.enrichment.EnrichmentSchedulerSettingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.*;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity.SubscriptionStatus;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity.DeliveryStatus;
import com.zillya.timonfech.zillwrapper.core.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseSubscriptionSchedulerService {

    public static final String JOB_NAME = "LICENSE_SUBSCRIPTION";
    private static final int DEFAULT_DELAY_MINUTES = 720;
    private static final String PRENOTIFY_REFRESH_MARKER = "PRENOTIFY_REFRESH_REQUESTED";
    private static final String DETAILED_REFRESH_MARKER = "DETAILED_REFRESH_REQUESTED";

    private final EnrichmentSchedulerSettingRepository schedulerSettingRepository;
    private final LicenseSubscriptionRepository subscriptionRepository;
    private final LicenseRepository licenseRepository;
    private final OrderItemRepository orderItemRepository;
    private final EnrichmentTaskManager enrichmentTaskManager;
    private final SubscriptionWarningDeliveryRepository warningDeliveryRepository;
    @Value("${subscription.prenotify.extra-days:1}")
    private int prenotifyExtraDays;
    @Value("${subscription.missing-expiration.backoff-minutes:720}")
    private int missingExpirationBackoffMinutes;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public EnrichmentSchedulerSettingEntity getSettings() {
        return schedulerSettingRepository.findById(JOB_NAME).orElseGet(this::createDefaults);
    }

    @Transactional
    public EnrichmentSchedulerSettingEntity setEnabled(boolean enabled, String updatedBy) {
        EnrichmentSchedulerSettingEntity settings = getSettings();
        settings.setEnabled(enabled);
        settings.setUpdatedBy(updatedBy);
        settings.setUpdatedAt(Instant.now());
        return schedulerSettingRepository.save(settings);
    }

    @Transactional
    public EnrichmentSchedulerSettingEntity setDelayMinutes(int delayMinutes, String updatedBy) {
        if (delayMinutes < 1) {
            throw new IllegalArgumentException("Delay must be >= 1 minute");
        }
        EnrichmentSchedulerSettingEntity settings = getSettings();
        settings.setDelayMinutes(delayMinutes);
        settings.setUpdatedBy(updatedBy);
        settings.setUpdatedAt(Instant.now());
        return schedulerSettingRepository.save(settings);
    }

    @Scheduled(fixedDelayString = "${subscription.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            EnrichmentSchedulerSettingEntity settings = getSettings();
            if (!settings.isEnabled()) {
                return;
            }
            Instant now = Instant.now();
            List<LicenseSubscriptionEntity> active = subscriptionRepository.findByStatusInAndNextCheckAtBefore(
                    List.of(SubscriptionStatus.ACTIVE),
                    now
            );
            List<SubscriptionStatus> dueStatuses = List.of(SubscriptionStatus.ACTIVE);
            log.debug("Subscription scheduler tick: now={} dueStatuses={} dueSubscriptions={}", now, dueStatuses, active.size());
            if (active.isEmpty()) {
                List<LicenseSubscriptionEntity> snapshot = subscriptionRepository.findTop50ByStatusInOrderByNextCheckAtAsc(dueStatuses);
                if (snapshot.isEmpty()) {
                    log.debug("Subscription scheduler snapshot: no ACTIVE/WARNING_SENT subscriptions in license_subscription_entity");
                } else {
                    for (LicenseSubscriptionEntity sub : snapshot) {
                        log.debug("Subscription snapshot row: subId={} status={} licenseId={} orderId={} sourceId={} nextCheckAt={} expectedExp={} notifiedAt={} warningLead={} {} lastError={}",
                                sub.getId(),
                                sub.getStatus(),
                                sub.getLicenseId(),
                                sub.getOrderId(),
                                sub.getSourceId(),
                                sub.getNextCheckAt(),
                                sub.getExpectedExpiration(),
                                sub.getNotifiedAt(),
                                sub.getWarningLeadAmount(),
                                sub.getWarningLeadUnit(),
                                sub.getLastError());
                    }
                }
                List<LicenseSubscriptionEntity> allActive = subscriptionRepository.findTop200ByStatusOrderByIdAsc(SubscriptionStatus.ACTIVE);
                log.debug("Subscription scheduler ACTIVE snapshot: totalActiveRows={}", allActive.size());
                for (LicenseSubscriptionEntity sub : allActive) {
                    log.debug("Subscription ACTIVE row: subId={} licenseId={} orderId={} sourceId={} nextCheckAt={} expectedExp={} notifiedAt={} warningLead={} {} lastError={}",
                            sub.getId(),
                            sub.getLicenseId(),
                            sub.getOrderId(),
                            sub.getSourceId(),
                            sub.getNextCheckAt(),
                            sub.getExpectedExpiration(),
                            sub.getNotifiedAt(),
                            sub.getWarningLeadAmount(),
                            sub.getWarningLeadUnit(),
                            sub.getLastError());
                }
            }

            for (LicenseSubscriptionEntity sub : active) {
                processSubscription(sub, now);
            }
        } finally {
            running.set(false);
        }
    }

    private void processSubscription(LicenseSubscriptionEntity sub, Instant now) {
        LicenseEntity license = sub.getLicenseId() == null ? null : licenseRepository.findById(sub.getLicenseId()).orElse(null);
        boolean onlineLicense = resolveOnlineModeFromOrder(license);
        log.debug("Sub process start: subId={} status={} licenseId={} orderId={} sourceId={} online={} expectedExp={} nextCheckAt={} notifiedAt={} lead={} {}",
                sub.getId(),
                sub.getStatus(),
                sub.getLicenseId(),
                sub.getOrderId(),
                sub.getSourceId(),
                onlineLicense,
                sub.getExpectedExpiration(),
                sub.getNextCheckAt(),
                sub.getNotifiedAt(),
                sub.getWarningLeadAmount(),
                sub.getWarningLeadUnit());
        if (onlineLicense && license != null && license.getExpiresAt() != null) {
            sub.setExpectedExpiration(license.getExpiresAt());
            log.debug("Sub expectedExpiration refreshed from online license: subId={} licenseId={} expiresAt={}",
                    sub.getId(), license.getId(), license.getExpiresAt());
        } else if (!onlineLicense && license != null && license.getExpiresAt() != null) {
            // OFFLINE primary source: explicit expiresAt from enrich/manual data.
            sub.setExpectedExpiration(license.getExpiresAt());
            log.debug("Sub expectedExpiration refreshed from offline license.expiresAt: subId={} licenseId={} expiresAt={}",
                    sub.getId(), license.getId(), license.getExpiresAt());
        }

        if (isBlocked(license)) {
            sub.setStatus(SubscriptionStatus.UNSUBSCRIBED);
            sub.setNextCheckAt(null);
            sub.setLastError("AUTO_UNSUBSCRIBED_BLOCKED");
            sub.setUpdatedAt(now);
            log.debug("Sub auto-unsubscribed due to blocked license: subId={} licenseId={}", sub.getId(), sub.getLicenseId());
            subscriptionRepository.save(sub);
            return;
        }

        if (sub.getExpectedExpiration() == null) {
            if (onlineLicense) {
                sub.setLastError("MISSING_EXPECTED_EXPIRATION");
                scheduleNext(sub, Math.max(1, missingExpirationBackoffMinutes), now);
                log.debug("Sub missing expectedExpiration (online): subId={} -> nextCheckAt={} backoffMin={}",
                        sub.getId(), sub.getNextCheckAt(), Math.max(1, missingExpirationBackoffMinutes));
            } else {
                sub.setStatus(SubscriptionStatus.ERROR);
                sub.setLastError("MISSING_EXPECTED_EXPIRATION_OFFLINE");
                sub.setNextCheckAt(null);
                sub.setUpdatedAt(now);
                log.debug("Sub missing expectedExpiration (offline): subId={} -> status=ERROR", sub.getId());
            }
            subscriptionRepository.save(sub);
            return;
        }

        long leadMinutes = warningToMinutes(
                sub.getWarningLeadAmount() == null ? 3 : sub.getWarningLeadAmount(),
                sub.getWarningLeadUnit() == null ? SubscriptionLeadUnit.DAY : sub.getWarningLeadUnit()
        );
        long preNotifyExtraMinutes = Math.max(1, prenotifyExtraDays) * 24L * 60L;
        Instant warningAt = sub.getExpectedExpiration().minus(leadMinutes, ChronoUnit.MINUTES);
        Instant preNotifyAt = warningAt.minus(preNotifyExtraMinutes, ChronoUnit.MINUTES);
        boolean detailedOnline = onlineLicense && Boolean.TRUE.equals(sub.getDetailed());
        if (detailedOnline && isActivated(license) && sub.getActivatedAt() == null) {
            sub.setActivatedAt(now);
        }
        log.debug("Sub computed windows: subId={} now={} expectedExp={} warningAt={} preNotifyAt={} leadMin={} preExtraMin={}",
                sub.getId(), now, sub.getExpectedExpiration(), warningAt, preNotifyAt, leadMinutes, preNotifyExtraMinutes);

        if (sub.getExpectedExpiration().isBefore(now)) {
            if (sub.getNotifiedAt() == null) {
                log.debug("Sub expired before warning delivery. Queueing late warning: subId={} expectedExp={} now={}",
                        sub.getId(), sub.getExpectedExpiration(), now);
                queueWarningDelivery(sub, license, onlineLicense, now, leadMinutes);
                // Keep it schedulable until delivery succeeds/fails; dispatcher owns actual send attempts.
                scheduleAt(sub, now, now);
                subscriptionRepository.save(sub);
                return;
            }
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setNextCheckAt(null);
            sub.setUpdatedAt(now);
            log.debug("Sub expired: subId={} expectedExp={} now={} -> status=EXPIRED",
                    sub.getId(), sub.getExpectedExpiration(), now);
            subscriptionRepository.save(sub);
            return;
        }

        if (sub.getNotifiedAt() != null) {
            scheduleAt(sub, sub.getExpectedExpiration(), now);
            log.debug("Sub already notified: subId={} notifiedAt={} -> nextCheckAt={}",
                    sub.getId(), sub.getNotifiedAt(), sub.getNextCheckAt());
            subscriptionRepository.save(sub);
            return;
        }

        Instant detailedNextAt = null;
        if (detailedOnline && license != null && license.getExternalId() != null) {
            requestOnlineDetailedRefresh(license, sub, now);
            detailedNextAt = now.plus(isActivated(license) ? 30 : 1, ChronoUnit.DAYS);
        }

        if (onlineLicense && requiresPreNotifyRefresh(license, sub)) {
            if (preNotifyAt.isAfter(now)) {
                Instant candidate = earlier(preNotifyAt, detailedNextAt);
                scheduleAt(sub, candidate, now);
                log.debug("Sub waiting prenotify window: subId={} preNotifyAt={} -> nextCheckAt={}",
                        sub.getId(), preNotifyAt, sub.getNextCheckAt());
                subscriptionRepository.save(sub);
                return;
            }
            requestOnlinePreNotifyRefresh(license, sub, now);
            Instant candidate = earlier(warningAt, detailedNextAt);
            scheduleAt(sub, candidate, now);
            log.debug("Sub prenotify refresh requested: subId={} -> nextCheckAt={}",
                    sub.getId(), sub.getNextCheckAt());
            subscriptionRepository.save(sub);
            return;
        }

        if (warningAt.isAfter(now)) {
            Instant candidate = earlier(warningAt, detailedNextAt);
            scheduleAt(sub, candidate, now);
            log.debug("Sub waiting warning window: subId={} warningAt={} -> nextCheckAt={}",
                    sub.getId(), warningAt, sub.getNextCheckAt());
            subscriptionRepository.save(sub);
            return;
        }

        log.debug("Sub warning window reached: subId={} now={} warningAt={}", sub.getId(), now, warningAt);
        queueWarningDelivery(sub, license, onlineLicense, now, leadMinutes);
        Instant candidate = earlier(sub.getExpectedExpiration(), detailedNextAt);
        scheduleAt(sub, candidate, now);
        log.debug("Sub post-queue scheduling: subId={} nextCheckAt={}", sub.getId(), sub.getNextCheckAt());
        subscriptionRepository.save(sub);
    }

    private void scheduleNext(LicenseSubscriptionEntity sub, int delayMinutes, Instant now) {
        sub.setNextCheckAt(now.plus(Math.max(1, delayMinutes), ChronoUnit.MINUTES));
        sub.setUpdatedAt(now);
    }

    private void scheduleAt(LicenseSubscriptionEntity sub, Instant candidate, Instant now) {
        Instant next = candidate == null || candidate.isBefore(now) ? now : candidate;
        sub.setNextCheckAt(next);
        sub.setUpdatedAt(now);
    }

    private boolean requiresPreNotifyRefresh(LicenseEntity license, LicenseSubscriptionEntity sub) {
        if (license == null || license.getExternalId() == null || sub.getSourceId() == null) {
            return false;
        }
        Long markerVersion = expectedExpirationVersion(sub.getExpectedExpiration());
        if (markerVersion == null) {
            return sub.getLastError() == null || !sub.getLastError().startsWith(PRENOTIFY_REFRESH_MARKER);
        }
        String marker = PRENOTIFY_REFRESH_MARKER + ":" + markerVersion;
        return sub.getLastError() == null || !sub.getLastError().startsWith(marker);
    }

    private boolean isBlocked(LicenseEntity license) {
        if (license == null || license.getStatus() == null) {
            return false;
        }
        return license.getStatus() == LicenseStatus.BLOCKED
                || license.getStatus() == LicenseStatus.BLOCKED_NEW
                || license.getStatus() == LicenseStatus.BLOCKED_OVER;
    }

    private void requestOnlinePreNotifyRefresh(LicenseEntity license, LicenseSubscriptionEntity sub, Instant now) {
        try {
            EnrichmentRequest request = new EnrichmentRequest(
                    UUID.randomUUID(),
                    sub.getSourceId(),
                    EntityTypeEnum.LICENSE,
                    license.getBrandId(),
                    license.getProductId(),
                    null,
                    license.getExternalId(),
                    -1,
                    0
            );
            enrichmentTaskManager.startEnrichment(request);
            Long markerVersion = expectedExpirationVersion(sub.getExpectedExpiration());
            sub.setLastError(PRENOTIFY_REFRESH_MARKER + ":" + (markerVersion == null ? now.toEpochMilli() : markerVersion));
            log.debug("Requested pre-notify enrich for online licenseId={} externalId={}", license.getId(), license.getExternalId());
        } catch (Exception ex) {
            sub.setLastError("PRENOTIFY_REFRESH_FAILED: " + ex.getMessage());
            log.warn("Failed to request pre-notify enrich for licenseId={}: {}", license.getId(), ex.getMessage());
        }
    }

    private void requestOnlineDetailedRefresh(LicenseEntity license, LicenseSubscriptionEntity sub, Instant now) {
        try {
            EnrichmentRequest request = new EnrichmentRequest(
                    UUID.randomUUID(),
                    sub.getSourceId(),
                    EntityTypeEnum.LICENSE,
                    license.getBrandId(),
                    license.getProductId(),
                    null,
                    license.getExternalId(),
                    -1,
                    0
            );
            enrichmentTaskManager.startEnrichment(request);
            sub.setLastError(DETAILED_REFRESH_MARKER + ":" + now.toEpochMilli());
            log.debug("Requested detailed enrich for online licenseId={} externalId={}", license.getId(), license.getExternalId());
        } catch (Exception ex) {
            sub.setLastError("DETAILED_REFRESH_FAILED: " + ex.getMessage());
            log.warn("Failed to request detailed enrich for licenseId={}: {}", license.getId(), ex.getMessage());
        }
    }

    private boolean isOnlineLicense(LicenseEntity license) {
        if (license == null || license.getKey() == null) {
            return false;
        }
        String online = license.getKey().getOnlineKey();
        return online != null && !online.isBlank();
    }

    private boolean isActivated(LicenseEntity license) {
        if (license == null || license.getKey() == null) {
            return false;
        }
        if (license.getKey() instanceof DinoKeyEntity dinoKey && dinoKey.getActivations() != null) {
            return dinoKey.getActivations().stream().anyMatch(this::isMeaningfulDinoActivation);
        }
        if (license.getKey() instanceof WhiteAdminKeyEntity whiteAdminKey && whiteAdminKey.getActivations() != null) {
            return whiteAdminKey.getActivations().stream().anyMatch(this::isMeaningfulWhiteAdminActivation);
        }
        return false;
    }

    private boolean isMeaningfulDinoActivation(LicenseActivationEntity activation) {
        if (activation == null) {
            return false;
        }
        return activation.getCreated() != null
                || activation.getLastRequest() != null
                || (activation.getLastSuccess() != null && !activation.getLastSuccess().isBlank());
    }

    private boolean isMeaningfulWhiteAdminActivation(WhiteAdminActivationEntity activation) {
        if (activation == null) {
            return false;
        }
        return activation.getFirstActivation() != null || activation.getLastActivation() != null;
    }

    private boolean resolveOnlineModeFromOrder(LicenseEntity license) {
        if (license != null && license.getOrderItemId() != null) {
            OrderItemEntity item = orderItemRepository.findById(license.getOrderItemId()).orElse(null);
            if (item != null && item.getKeyTypes() != null && !item.getKeyTypes().isEmpty()) {
                // Source of truth: requested key type(s) in the order item.
                boolean hasOffline = item.getKeyTypes().contains(KeyType.OFFLINE);
                boolean hasOnline = item.getKeyTypes().contains(KeyType.ONLINE);
                if (hasOffline && !hasOnline) {
                    return false;
                }
                if (hasOnline) {
                    return true;
                }
            }
        }
        // Fallback for legacy/partial data.
        return isOnlineLicense(license);
    }

    private boolean canSendWarningNow(LicenseEntity license, boolean onlineLicense) {
        if (onlineLicense) {
            if (license == null || license.getExpiresAt() == null) {
                return false;
            }
            return !isBlocked(license);
        }
        return true;
    }

    private Long expectedExpirationVersion(Instant expectedExpiration) {
        return expectedExpiration == null ? null : expectedExpiration.toEpochMilli();
    }

    private void queueWarningDelivery(LicenseSubscriptionEntity sub,
                                      LicenseEntity license,
                                      boolean onlineLicense,
                                      Instant now,
                                      long leadMinutes) {
        if (onlineLicense && (license == null || license.getExternalId() == null)) {
            Instant warningWindowAt = sub.getExpectedExpiration().minus(leadMinutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
            SubscriptionWarningDeliveryEntity deferred = warningDeliveryRepository
                    .findBySubscriptionIdAndWindowAt(sub.getId(), warningWindowAt)
                    .orElseGet(SubscriptionWarningDeliveryEntity::new);
            deferred.setSubscriptionId(sub.getId());
            deferred.setLicenseId(sub.getLicenseId());
            deferred.setOrderId(sub.getOrderId());
            deferred.setSourceId(sub.getSourceId());
            deferred.setWindowAt(warningWindowAt);
            deferred.setStatus(DeliveryStatus.DEFERRED_NO_EXTERNAL_ID);
            deferred.setNextAttemptAt(now.plus(60, ChronoUnit.MINUTES));
            deferred.setLastError("externalId is null");
            warningDeliveryRepository.save(deferred);
            log.debug("Warning delivery deferred (no externalId): subId={} licenseId={} deliveryId={} windowAt={} nextAttemptAt={}",
                    sub.getId(), sub.getLicenseId(), deferred.getId(), warningWindowAt, deferred.getNextAttemptAt());
            return;
        }
        if (!canSendWarningNow(license, onlineLicense)) {
            log.debug("Warning delivery skipped by canSendWarningNow: subId={} licenseId={} online={} blockedOrInvalid=true",
                    sub.getId(), sub.getLicenseId(), onlineLicense);
            return;
        }
        Instant windowAt = sub.getExpectedExpiration().minus(leadMinutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        SubscriptionWarningDeliveryEntity delivery = warningDeliveryRepository
                .findBySubscriptionIdAndWindowAt(sub.getId(), windowAt)
                .orElseGet(SubscriptionWarningDeliveryEntity::new);
        if (delivery.getStatus() == DeliveryStatus.SENT || delivery.getStatus() == DeliveryStatus.GAVE_UP) {
            log.debug("Warning delivery already terminal: subId={} deliveryId={} status={}",
                    sub.getId(), delivery.getId(), delivery.getStatus());
            return;
        }
        delivery.setSubscriptionId(sub.getId());
        delivery.setLicenseId(sub.getLicenseId());
        delivery.setOrderId(sub.getOrderId());
        delivery.setSourceId(sub.getSourceId());
        delivery.setWindowAt(windowAt);
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setNextAttemptAt(now);
        if (delivery.getAttemptCount() < 0) {
            delivery.setAttemptCount(0);
        }
        warningDeliveryRepository.save(delivery);
        log.debug("Warning delivery queued: subId={} deliveryId={} windowAt={} nextAttemptAt={} status={}",
                sub.getId(), delivery.getId(), delivery.getWindowAt(), delivery.getNextAttemptAt(), delivery.getStatus());
    }

    private Instant earlier(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private long warningToMinutes(int amount, SubscriptionLeadUnit unit) {
        return switch (unit) {
            case MINUTE -> amount;
            case HOUR -> amount * 60L;
            case DAY -> amount * 24L * 60L;
            case MONTH -> amount * 30L * 24L * 60L;
        };
    }

    private EnrichmentSchedulerSettingEntity createDefaults() {
        EnrichmentSchedulerSettingEntity settings = new EnrichmentSchedulerSettingEntity();
        settings.setJobName(JOB_NAME);
        settings.setEnabled(true);
        settings.setDelayMinutes(DEFAULT_DELAY_MINUTES);
        settings.setUpdatedBy("system");
        settings.setUpdatedAt(Instant.now());
        return schedulerSettingRepository.save(settings);
    }
}
