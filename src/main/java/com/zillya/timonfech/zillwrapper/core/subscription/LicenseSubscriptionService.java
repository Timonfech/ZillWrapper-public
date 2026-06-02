package com.zillya.timonfech.zillwrapper.core.subscription;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity.SubscriptionStatus;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientType;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseSubscriptionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseSubscriptionService {

    private static final int DEFAULT_WARNING_DAYS = 3;

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final LicenseRepository licenseRepository;
    private final LicenseSubscriptionRepository subscriptionRepository;
    private final WarningLeadParser warningLeadParser;

    @Transactional
    public SubscriptionSetupSummary setupSubscriptionsForOrder(Long orderId,
                                                               Long sourceId,
                                                               Long initiatorUserId,
                                                               List<OrderItemSpec> requestedSpecs) {
        if (orderId == null) {
            return new SubscriptionSetupSummary(0, 0, List.of("Order id is null"));
        }
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        boolean partnerClient = order != null
                && order.getClient() != null
                && order.getClient().getClientType() == ClientType.PARTNER;

        List<OrderItemEntity> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        List<OrderItemSpec> specs = requestedSpecs == null ? List.of() : requestedSpecs;
        boolean alignedSpecs = specs.size() == items.size();
        if (!specs.isEmpty() && !alignedSpecs) {
            log.warn("Order/specs size mismatch for subscription setup. orderId={} orderItems={} specs={}. Fallback to DB-only setup.",
                    orderId, items.size(), specs.size());
        }
        int createdOrUpdated = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            OrderItemEntity item = items.get(i);
            OrderItemSpec spec = alignedSpecs ? specs.get(i) : null;
            if (!isSubscribed(spec, partnerClient)) {
                continue;
            }
            List<LicenseEntity> licenses = licenseRepository.findByOrderItemId(item.getId());
            for (LicenseEntity license : licenses) {
                try {
                    upsertSubscription(item, spec, license, sourceId, initiatorUserId);
                    createdOrUpdated++;
                } catch (Exception ex) {
                    errors.add("licenseId=" + license.getId() + ": " + ex.getMessage());
                    markSubscriptionError(license, sourceId, initiatorUserId, ex.getMessage());
                    log.warn("Failed to setup subscription for license {}: {}", license.getId(), ex.getMessage());
                }
            }
        }

        return new SubscriptionSetupSummary(createdOrUpdated, errors.size(), List.copyOf(errors));
    }

    @Transactional
    protected void markSubscriptionError(LicenseEntity license,
                                         Long sourceId,
                                         Long initiatorUserId,
                                         String error) {
        if (license == null || license.getId() == null) {
            return;
        }
        LicenseSubscriptionEntity sub = subscriptionRepository.findByLicenseId(license.getId())
                .orElseGet(LicenseSubscriptionEntity::new);
        sub.setLicenseId(license.getId());
        sub.setOrderId(license.getOrderId());
        sub.setSourceId(sourceId);
        sub.setInitiatorUserId(initiatorUserId);
        sub.setStatus(SubscriptionStatus.ERROR);
        sub.setLastError(error);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    @Transactional
    protected void upsertSubscription(OrderItemEntity item,
                                      OrderItemSpec spec,
                                      LicenseEntity license,
                                      Long sourceId,
                                      Long initiatorUserId) {
        LicenseSubscriptionEntity sub = subscriptionRepository.findByLicenseId(license.getId())
                .orElseGet(LicenseSubscriptionEntity::new);

        sub.setLicenseId(license.getId());
        sub.setOrderId(license.getOrderId());
        sub.setSourceId(sourceId);
        sub.setInitiatorUserId(initiatorUserId);
        sub.setDetailed(spec != null && spec.options() != null && Boolean.TRUE.equals(spec.options().subscriptionDetailed()));
        sub.setNotifyClient(spec != null && spec.options() != null && Boolean.TRUE.equals(spec.options().notifyClient()));
        sub.setCheckIntervalMinutes(spec != null && spec.options() != null ? spec.options().subscriptionIntervalMinutes() : null);

        WarningLead warningLead = spec != null && spec.options() != null
                ? warningLeadParser.parse(spec.options().warningLeadRaw()).orElse(null)
                : null;
        int warningAmount = warningLead != null ? warningLead.amount() : DEFAULT_WARNING_DAYS;
        SubscriptionLeadUnit warningUnit = warningLead != null ? warningLead.unit() : SubscriptionLeadUnit.DAY;
        sub.setWarningLeadAmount(warningAmount);
        sub.setWarningLeadUnit(warningUnit);

        Instant now = Instant.now();
        sub.setUpdatedAt(now);
        if (sub.getStatus() == null) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
        }

        boolean onlineRequested = item.getKeyTypes() != null && item.getKeyTypes().contains(KeyType.ONLINE);
        boolean offlineOnly = !onlineRequested;
        if (offlineOnly) {
            // OFFLINE primary source: explicit expiresAt (if already known), fallback to createdAtOrigin + term.
            Instant expected = license.getExpiresAt() != null
                    ? license.getExpiresAt()
                    : computeOfflineExpectedExpiration(license, item, now);
            sub.setExpectedExpiration(expected);
            sub.setActivatedAt(license.getCreatedAtOrigin() != null ? license.getCreatedAtOrigin() : license.getCreatedAt());
        } else if (license.getExpiresAt() != null) {
            sub.setExpectedExpiration(license.getExpiresAt());
        }

        if (sub.getNextCheckAt() == null) {
            sub.setNextCheckAt(now.plus(3, ChronoUnit.MINUTES));
        }
        subscriptionRepository.save(sub);
    }

    private boolean isSubscribed(OrderItemSpec spec, boolean partnerClient) {
        if (spec == null) {
            return !partnerClient;
        }
        boolean subscribeExplicit = spec.options() != null && spec.options().subscribeExplicit() != null
                ? spec.options().subscribeExplicit()
                : false;
        boolean finalSubscribed = partnerClient && !subscribeExplicit ? false : spec.subscribed();
        return finalSubscribed;
    }

    private Instant computeOfflineExpectedExpiration(LicenseEntity license, OrderItemEntity item, Instant fallbackNow) {
        Instant base = license.getCreatedAtOrigin();
        if (base == null) {
            base = license.getCreatedAt();
        }
        if (base == null) {
            base = fallbackNow;
        }
        if (item.getPeriodAmount() == null || item.getPeriodUnit() == null) {
            return base.plus(365, ChronoUnit.DAYS);
        }
        return switch (item.getPeriodUnit()) {
            case DAY -> base.plus(item.getPeriodAmount(), ChronoUnit.DAYS);
            case MONTH -> base.plus(item.getPeriodAmount() * 30L, ChronoUnit.DAYS);
            case YEAR -> base.plus(item.getPeriodAmount() * 365L, ChronoUnit.DAYS);
        };
    }

    public record SubscriptionSetupSummary(int createdOrUpdated, int failed, List<String> errors) {
    }
}
