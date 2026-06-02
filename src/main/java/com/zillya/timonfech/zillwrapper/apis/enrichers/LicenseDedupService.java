package com.zillya.timonfech.zillwrapper.apis.enrichers;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LicenseAggregate;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LicenseUpsertResult;
import com.zillya.timonfech.zillwrapper.core.entities.license.*;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.pipeline.LegacyCommentLicenseParser;
import com.zillya.timonfech.zillwrapper.core.repos.ClientRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseSubscriptionRepository;
import com.zillya.timonfech.zillwrapper.core.services.persistance.OrderPersistenceService;
import com.zillya.timonfech.zillwrapper.core.subscription.events.SubscriptionDetailedDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseDedupService {

    private final LicenseRepository licenseRepository;
    private final ClientRepository clientRepository;
    private final LicenseSubscriptionRepository subscriptionRepository;
    private final OrderPersistenceService orderProcessingService;
    private final ApplicationEventPublisher eventPublisher;
    private final LegacyCommentLicenseParser commentLicenseParser;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Transactional
    public LicenseUpsertResult upsert(EnrichmentRequest ctx, LicenseAggregate aggregate) {
        LicenseEntity incoming = aggregate.license();
        incoming.setSourceId(ctx.sourceId());
        normalizeIncomingKeys(incoming);

        ClientEntity savedClient = null;
        boolean clientChanged = false;

        if (isMeaningfulClient(aggregate.client())) {
            savedClient = clientRepository.save(aggregate.client());
            clientChanged = true;
        }

        OrderEntity savedOrder = null;
        boolean orderChanged = false;

        if (isMeaningfulOrder(aggregate.order())) {
            // if (savedClient != null) {
            // aggregate.order().setClient(savedClient);
            // }
            savedOrder = orderProcessingService.saveOrUpdate(aggregate.order());
            orderChanged = true;
        }

        if (savedOrder != null && savedOrder.getId() != null) {
            incoming.setOrderId(savedOrder.getId());
            log.info("license_bound_to_order incomingExternalId={} orderId={}",
                    incoming.getExternalId(), savedOrder.getId());
            relinkLicensesFromComment(savedOrder, incoming.getDescription());
        }

        LicenseEntity target = null;
        boolean stopScanning = false;
        boolean licenseInfoUpdated = false;

        if (ctx.entityId() != null) {
            // entityId in enrichment request is an external (source) id, not local DB PK.
            target = licenseRepository.findByExternalId(ctx.entityId()).orElse(null);

            if (target != null) {
                Snapshot before = Snapshot.from(target);
                if (keysConflict(target, incoming)) {
                    String targetKeyPrefix = keyPrefix(target.getKey() == null ? null : target.getKey().getOnlineKey());
                    String incomingKeyPrefix = keyPrefix(incoming.getKey() == null ? null : incoming.getKey().getOnlineKey());
                    log.error("License enrich consistency violation: externalId={} targetLicenseId={} conflictType=online_key_mismatch targetKeyPrefix={} incomingKeyPrefix={}",
                            ctx.entityId(), target.getId(), targetKeyPrefix, incomingKeyPrefix);
                    throw new IllegalStateException("Consistency violation: externalId maps to different onlineKey");
                }
                mergeLicense(target, incoming);
                target = licenseRepository.save(target);
                licenseInfoUpdated = true;
//                log.info("License upsert resolveMode=externalId incomingExternalId={} matchedLicenseId={}",
//                        incoming.getExternalId(), target.getId());
                publishDetailedDeltaIfNeeded(ctx.sourceId(), target, before);

                return new LicenseUpsertResult(
                        target.getId(),
                        savedClient != null ? savedClient.getId() : null,
                        savedOrder != null ? savedOrder.getId() : null,
                        licenseInfoUpdated,
                        clientChanged,
                        orderChanged,
                        false);
            }
        }

        target = findExistingByIncoming(incoming);

        if (target == null) {
            target = new LicenseEntity();
            licenseInfoUpdated = true; // New entity is always an update
            if (target.getVersionNo() == null) {
                target.setVersionNo(1L);
            }
//            log.info("License upsert resolveMode=created incomingExternalId={}", incoming.getExternalId());
        } else {
            validateImmutableFields(target, incoming);
//            log.info("License upsert resolveMode=key incomingExternalId={} matchedLicenseId={}",
//                    incoming.getExternalId(), target.getId());
        }

        Snapshot before = target.getId() == null ? null : Snapshot.from(target);
        if (mergeLicense(target, incoming)) {
            licenseInfoUpdated = true;
        }

        if (licenseInfoUpdated) {
            target = licenseRepository.save(target);
            publishDetailedDeltaIfNeeded(ctx.sourceId(), target, before);
        }

        return new LicenseUpsertResult(
                target.getId(),
                savedClient != null ? savedClient.getId() : null,
                savedOrder != null ? savedOrder.getId() : null,
                licenseInfoUpdated,
                clientChanged,
                orderChanged,
                stopScanning);
    }

    private LicenseEntity findExistingByIncoming(LicenseEntity incoming) {
        if (incoming.getExternalId() != null) {
            LicenseEntity byExternalId = licenseRepository.findByExternalId(incoming.getExternalId()).orElse(null);
            if (byExternalId != null) {
                return byExternalId;
            }
        }

        LicenseEntity byOnline = findByOnlineKey(incoming);
        if (byOnline != null) {
            return byOnline;
        }

        return null;
    }

    private LicenseEntity findByOnlineKey(LicenseEntity incoming) {
        if (incoming.getKey() == null) {
            return null;
        }
        String online = normalizeKey(incoming.getKey().getOnlineKey());
        if (online == null) {
            return null;
        }
        LicenseEntity byOnline = licenseRepository.findFirstByKey_OnlineKeyIgnoreCase(online).orElse(null);
        if (byOnline != null) {
            return byOnline;
        }
        String offline = normalizeKey(incoming.getKey().getOfflineKey());
        if (offline == null) {
            return null;
        }
        return licenseRepository.findFirstByKey_OfflineKeyIgnoreCase(offline).orElse(null);
    }

    private void normalizeIncomingKeys(LicenseEntity incoming) {
        if (incoming == null || incoming.getKey() == null) {
            return;
        }
        incoming.getKey().setOnlineKey(normalizeKey(incoming.getKey().getOnlineKey()));
        incoming.getKey().setOfflineKey(normalizeKey(incoming.getKey().getOfflineKey()));
    }

    private String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String withoutMarkers = KeyMarkersUtils.removeMarkers(raw);
        String collapsed = withoutMarkers.replaceAll("\\s+", "");
        if (collapsed.isBlank()) {
            return null;
        }
        return collapsed.trim().toUpperCase(Locale.ROOT);
    }

    private boolean keysConflict(LicenseEntity target, LicenseEntity incoming) {
        if (target == null || incoming == null || target.getKey() == null || incoming.getKey() == null) {
            return false;
        }
        String targetOnline = normalizeKey(target.getKey().getOnlineKey());
        String incomingOnline = normalizeKey(incoming.getKey().getOnlineKey());
        return targetOnline != null && incomingOnline != null && !Objects.equals(targetOnline, incomingOnline);
    }

    private String keyPrefix(String key) {
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return "null";
        }
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    private boolean mergeLicense(LicenseEntity target, LicenseEntity incoming) {
        boolean changed = false;
        boolean existingEntity = target.getId() != null;

        if (target.getVersionNo() == null) {
            target.setVersionNo(1L);
            changed = true;
        }

        if (incoming.getExternalId() != null && !Objects.equals(target.getExternalId(), incoming.getExternalId())) {
            target.setExternalId(incoming.getExternalId());
            changed = true;
        }

        if (incoming.getBrandId() != null && !Objects.equals(target.getBrandId(), incoming.getBrandId())) {
            target.setBrandId(incoming.getBrandId());
            changed = true;
        }

        if (incoming.getProductId() != null && !Objects.equals(target.getProductId(), incoming.getProductId())) {
            target.setProductId(incoming.getProductId());
            changed = true;
        }

        if (incoming.getDevices() != null && !Objects.equals(target.getDevices(), incoming.getDevices())) {
            target.setDevices(incoming.getDevices());
            changed = true;
        }

        if (incoming.getCreatedAt() != null && !Objects.equals(target.getCreatedAt(), incoming.getCreatedAt())) {
            target.setCreatedAt(incoming.getCreatedAt());
            changed = true;
        }

        if (incoming.getCreatedAtOrigin() != null
                && !Objects.equals(target.getCreatedAtOrigin(), incoming.getCreatedAtOrigin())) {
            target.setCreatedAtOrigin(incoming.getCreatedAtOrigin());
            changed = true;
        }

        if (incoming.getExpiresAt() != null && !Objects.equals(target.getExpiresAt(), incoming.getExpiresAt())) {
            target.setExpiresAt(incoming.getExpiresAt());
            changed = true;
        }

        if (incoming.getStatus() != null && !Objects.equals(target.getStatus(), incoming.getStatus())) {
            target.setStatus(incoming.getStatus());
            changed = true;
        }

        if (incoming.getDescription() != null && !Objects.equals(target.getDescription(), incoming.getDescription())) {
            target.setDescription(incoming.getDescription());
            changed = true;
        }

        if ((incoming.getBusinessPeriod() != null || incoming.getPeriodAmount() != null || incoming.getPeriodUnit() != null)
                && (!Objects.equals(target.getPeriodAmount(), incoming.getPeriodAmount())
                || !Objects.equals(target.getPeriodUnit(), incoming.getPeriodUnit()))) {
            target.setBusinessPeriod(incoming.getBusinessPeriod());
            changed = true;
        }

        if (incoming.getClientId() != null && !Objects.equals(target.getClientId(), incoming.getClientId())) {
            target.setClientId(incoming.getClientId());
            changed = true;
        }

        if (incoming.getOrderId() != null && !Objects.equals(target.getOrderId(), incoming.getOrderId())) {
            target.setOrderId(incoming.getOrderId());
            changed = true;
        }

        if (incoming.getSourceId() != null && !Objects.equals(target.getSourceId(), incoming.getSourceId())) {
            if (target.getSourceId() == null) {
                target.setSourceId(incoming.getSourceId());
                changed = true;
            } else {
                log.debug("license_source_conflict_skip_overwrite licenseId={} existingSourceId={} incomingSourceId={}",
                        target.getId(),
                        target.getSourceId(),
                        incoming.getSourceId());
            }
        }

        if (incoming.getKey() != null) {
            BaseKeyEntity incomingKey = incoming.getKey();
            BaseKeyEntity targetKey = target.getKey();

            if (targetKey == null) {
                target.setKey(incomingKey);
                changed = true;
            } else {
                if (incomingKey.getOnlineKey() != null
                        && !normalizedEquals(targetKey.getOnlineKey(), incomingKey.getOnlineKey())) {
                    if (normalizeKey(targetKey.getOnlineKey()) != null) {
                        throw new IllegalStateException("Immutable field violation: onlineKey change is not allowed");
                    }
                    targetKey.setOnlineKey(incomingKey.getOnlineKey());
                    changed = true;
                }
                if (incomingKey.getOfflineKey() != null
                        && !normalizedEquals(targetKey.getOfflineKey(), incomingKey.getOfflineKey())) {
                    if (normalizeKey(targetKey.getOfflineKey()) != null) {
                        throw new IllegalStateException("Immutable field violation: offlineKey change is not allowed");
                    }
                    targetKey.setOfflineKey(incomingKey.getOfflineKey());
                    changed = true;
                }

                if (incomingKey instanceof com.zillya.timonfech.zillwrapper.core.entities.license.DinoKeyEntity incomingDino
                        && targetKey instanceof com.zillya.timonfech.zillwrapper.core.entities.license.DinoKeyEntity targetDino) {
                    if (incomingDino.getActivations() != null) {
                        targetDino.getActivations().clear();
                        targetDino.getActivations().addAll(incomingDino.getActivations());
                        targetDino.getActivations().forEach(a -> a.setDinoKey(targetDino));
                        changed = true;
                    }
                }

                if (incomingKey instanceof com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity incomingWhite
                        && targetKey instanceof com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity targetWhite) {
                    if (incomingWhite.getCompany() != null && !Objects.equals(targetWhite.getCompany(), incomingWhite.getCompany())) {
                        targetWhite.setCompany(incomingWhite.getCompany());
                        changed = true;
                    }
                    if (incomingWhite.getReservedServers() != null && !Objects.equals(targetWhite.getReservedServers(), incomingWhite.getReservedServers())) {
                        targetWhite.setReservedServers(incomingWhite.getReservedServers());
                        changed = true;
                    }
                    if (incomingWhite.getActivations() != null) {
                        targetWhite.getActivations().clear();
                        targetWhite.getActivations().addAll(incomingWhite.getActivations());
                        targetWhite.getActivations().forEach(a -> a.setWhiteAdminKey(targetWhite));
                        changed = true;
                    }
                }
            }
        }
        if (changed && existingEntity) {
            target.setVersionNo((target.getVersionNo() == null ? 1L : target.getVersionNo()) + 1L);
        }
        return changed;
    }

    private void validateImmutableFields(LicenseEntity target, LicenseEntity incoming) {
        if (target == null || incoming == null) {
            return;
        }
        if (incoming.getBrandId() != null
                && target.getBrandId() != null
                && !Objects.equals(target.getBrandId(), incoming.getBrandId())) {
            throw new IllegalStateException("Immutable field violation: brandId change is not allowed");
        }
        if (incoming.getProductId() != null
                && target.getProductId() != null
                && !Objects.equals(target.getProductId(), incoming.getProductId())) {
            throw new IllegalStateException("Immutable field violation: productId change is not allowed");
        }
        if (target.getKey() != null && incoming.getKey() != null) {
            if (incoming.getKey().getOnlineKey() != null
                    && normalizeKey(target.getKey().getOnlineKey()) != null
                    && !normalizedEquals(target.getKey().getOnlineKey(), incoming.getKey().getOnlineKey())) {
                throw new IllegalStateException("Immutable field violation: onlineKey change is not allowed");
            }
            if (incoming.getKey().getOfflineKey() != null
                    && normalizeKey(target.getKey().getOfflineKey()) != null
                    && !normalizedEquals(target.getKey().getOfflineKey(), incoming.getKey().getOfflineKey())) {
                throw new IllegalStateException("Immutable field violation: offlineKey change is not allowed");
            }
        }
    }

    private boolean normalizedEquals(String a, String b) {
        return Objects.equals(normalizeKey(a), normalizeKey(b));
    }

    private boolean isMeaningfulClient(ClientEntity client) {
        if (client == null) {
            return false;
        }
        if (client.getName() != null && !client.getName().isBlank()) {
            return true;
        }
        if (client.getPhone() != null && !client.getPhone().isBlank()) {
            return true;
        }
        if (client.getContacts() == null || client.getContacts().isEmpty()) {
            return false;
        }
        return client.getContacts().stream().anyMatch(this::isMeaningfulContact);
    }

    private boolean isMeaningfulContact(ContactMethod contact) {
        if (contact instanceof EmailContact email) {
            return hasText(email.plainValue) || hasText(email.encryptedValue);
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void relinkLicensesFromComment(OrderEntity order, String comment) {
        if (order == null || order.getId() == null || comment == null || comment.isBlank()) {
            return;
        }
        LegacyCommentLicenseParser.ParseResult parsed = commentLicenseParser.parse(comment, "whiteadmin");
        for (LegacyCommentLicenseParser.Token token : parsed.tokens()) {
            String normalized = normalizeLookupKey(token.key());
            if (normalized == null) {
                continue;
            }
            LicenseEntity matched = resolveLicenseByToken(normalized).orElse(null);
            if (matched == null) {
                log.info("license_comment_token_not_found orderId={} key={}", order.getId(), normalized);
                continue;
            }
            if (Objects.equals(matched.getOrderId(), order.getId())) {
                log.info("license_comment_token_already_linked orderId={} licenseId={} key={}",
                        order.getId(), matched.getId(), normalized);
                continue;
            }
            matched.setOrderId(order.getId());
            licenseRepository.save(matched);
            log.info("license_relinked_from_comment orderId={} licenseId={} key={}",
                    order.getId(), matched.getId(), normalized);
        }
    }

    private java.util.Optional<LicenseEntity> resolveLicenseByToken(String token) {
        List<LicenseEntity> candidates = licenseRepository.findAllByOnlineOrOfflineContainsCi(token);
        return candidates.stream()
                .filter(l -> l.getKey() != null)
                .filter(l -> token.equals(normalizeLookupKey(l.getKey().getOnlineKey()))
                        || token.equals(normalizeLookupKey(l.getKey().getOfflineKey())))
                .findFirst();
    }

    private String normalizeLookupKey(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = KeyMarkersUtils.removeMarkers(value).replaceAll("\\s+", "").trim();
        return cleaned.isBlank() ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private boolean isMeaningfulOrder(OrderEntity order) {
        if (order == null) {
            return false;
        }
        return order.getWhiteAdminId() != null || order.getPortalId() != null;
    }

    private void publishDetailedDeltaIfNeeded(Long sourceId, LicenseEntity after, Snapshot before) {
        if (after == null || before == null || after.getId() == null) {
            return;
        }
        var subOpt = subscriptionRepository.findByLicenseId(after.getId());
        if (subOpt.isEmpty() || !Boolean.TRUE.equals(subOpt.get().getDetailed())) {
            return;
        }

        ActivationSnapshot afterActivation = activationSnapshot(after);
        List<SubscriptionDetailedDeltaEvent.FieldDelta> deltas = new ArrayList<>();
        addDelta(deltas, "status", before.status, asText(after.getStatus()));
        addDelta(deltas, "expiresAt", before.expiresAt, asText(after.getExpiresAt()));
        addDelta(deltas, "createdAtOrigin", before.createdAtOrigin, asText(after.getCreatedAtOrigin()));
        addDelta(deltas, "devices", before.devices, asText(after.getDevices()));
        addDelta(deltas, "periodAmount", before.periodAmount, asText(after.getPeriodAmount()));
        addDelta(deltas, "periodUnit", before.periodUnit, asText(after.getPeriodUnit()));
        addDelta(deltas, "activated", before.activated, afterActivation.activated);
        addDelta(deltas, "activationSuccessCount", before.activationSuccessCount, afterActivation.successCount);
        addDelta(deltas, "activationRequiredDevices", before.activationRequiredDevices, afterActivation.requiredDevices);
        addDelta(deltas, "activationHealth", before.activationHealth, afterActivation.health);

        if (deltas.isEmpty()) {
            return;
        }

        eventPublisher.publishEvent(new SubscriptionDetailedDeltaEvent(
                this,
                sourceId,
                subOpt.get().getOrderId(),
                after.getId(),
                keyPrefix(after.getKey() == null ? null : after.getKey().getOnlineKey()),
                Instant.now(),
                deltas
        ));
    }

    private void addDelta(List<SubscriptionDetailedDeltaEvent.FieldDelta> out,
                          String field,
                          String before,
                          String after) {
        if (!Objects.equals(before, after)) {
            out.add(new SubscriptionDetailedDeltaEvent.FieldDelta(field, emptyAsDash(before), emptyAsDash(after)));
        }
    }

    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return DTF.format(instant);
        }
        return String.valueOf(value);
    }

    private static class Snapshot {
        private final String status;
        private final String expiresAt;
        private final String createdAtOrigin;
        private final String devices;
        private final String periodAmount;
        private final String periodUnit;
        private final String activated;
        private final String activationSuccessCount;
        private final String activationRequiredDevices;
        private final String activationHealth;

        private Snapshot(String status,
                         String expiresAt,
                         String createdAtOrigin,
                         String devices,
                         String periodAmount,
                         String periodUnit,
                         String activated,
                         String activationSuccessCount,
                         String activationRequiredDevices,
                         String activationHealth) {
            this.status = status;
            this.expiresAt = expiresAt;
            this.createdAtOrigin = createdAtOrigin;
            this.devices = devices;
            this.periodAmount = periodAmount;
            this.periodUnit = periodUnit;
            this.activated = activated;
            this.activationSuccessCount = activationSuccessCount;
            this.activationRequiredDevices = activationRequiredDevices;
            this.activationHealth = activationHealth;
        }

        static Snapshot from(LicenseEntity entity) {
            ActivationSnapshot activation = activationSnapshot(entity);
            return new Snapshot(
                    entity.getStatus() == null ? null : entity.getStatus().name(),
                    entity.getExpiresAt() == null ? null : DTF.format(entity.getExpiresAt()),
                    entity.getCreatedAtOrigin() == null ? null : DTF.format(entity.getCreatedAtOrigin()),
                    entity.getDevices() == null ? null : String.valueOf(entity.getDevices()),
                    entity.getPeriodAmount() == null ? null : String.valueOf(entity.getPeriodAmount()),
                    entity.getPeriodUnit() == null ? null : entity.getPeriodUnit().name(),
                    activation.activated,
                    activation.successCount,
                    activation.requiredDevices,
                    activation.health
            );
        }
    }

    private static ActivationSnapshot activationSnapshot(LicenseEntity entity) {
        int requiredDevices = (entity == null || entity.getDevices() == null || entity.getDevices() <= 0) ? 1 : entity.getDevices();
        int successCount = 0;
        int meaningfulCount = 0;

        if (entity != null && entity.getKey() instanceof DinoKeyEntity dinoKey && dinoKey.getActivations() != null) {
            for (LicenseActivationEntity activation : dinoKey.getActivations()) {
                if (!isMeaningfulDinoActivation(activation)) {
                    continue;
                }
                meaningfulCount++;
                if (isDinoSuccess(activation == null ? null : activation.getLastSuccess())) {
                    successCount++;
                }
            }
        } else if (entity != null && entity.getKey() instanceof WhiteAdminKeyEntity whiteKey && whiteKey.getActivations() != null) {
            for (WhiteAdminActivationEntity activation : whiteKey.getActivations()) {
                if (!isMeaningfulWhiteAdminActivation(activation)) {
                    continue;
                }
                meaningfulCount++;
                if (activation != null && activation.getComputersActivated() != null && activation.getComputersActivated() > 0) {
                    successCount += activation.getComputersActivated();
                } else {
                    successCount++;
                }
            }
        }

        String activated = meaningfulCount > 0 ? "yes" : "no";
        String health = meaningfulCount == 0
                ? "NO_ACTIVATIONS"
                : (successCount >= requiredDevices ? "SUCCESS" : "INSUFFICIENT_SUCCESS");

        return new ActivationSnapshot(
                activated,
                String.valueOf(successCount),
                String.valueOf(requiredDevices),
                health
        );
    }

    private static boolean isMeaningfulDinoActivation(LicenseActivationEntity activation) {
        if (activation == null) {
            return false;
        }
        return activation.getCreated() != null
                || activation.getLastRequest() != null
                || hasTextStatic(activation.getLastSuccess());
    }

    private static boolean isMeaningfulWhiteAdminActivation(WhiteAdminActivationEntity activation) {
        if (activation == null) {
            return false;
        }
        return activation.getFirstActivation() != null || activation.getLastActivation() != null;
    }

    private static boolean isDinoSuccess(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("success")
                || normalized.equals("ok")
                || normalized.equals("1")
                || normalized.equals("true");
    }

    private static boolean hasTextStatic(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record ActivationSnapshot(
            String activated,
            String successCount,
            String requiredDevices,
            String health
    ) {}
}
