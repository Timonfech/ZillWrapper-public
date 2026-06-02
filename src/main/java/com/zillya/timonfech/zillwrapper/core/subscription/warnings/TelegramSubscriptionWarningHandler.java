package com.zillya.timonfech.zillwrapper.core.subscription.warnings;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.SubscriptionWarningDeliveryEntity;
import com.zillya.timonfech.zillwrapper.core.repos.*;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.subscription.LicenseExpirationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSubscriptionWarningHandler implements SubscriptionSourceWarningHandler {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SourceRepository sourceRepository;
    private final OperationExecutionRepository operationExecutionRepository;
    private final TelegramOperationBindingRepository bindingRepository;
    private final LicenseRepository licenseRepository;
    private final OrderItemRepository orderItemRepository;
    private final LicenseExpirationResolver licenseExpirationResolver;
    private final AbsSender telegramSender;

    @Override
    public boolean supports(Long sourceId) {
        if (sourceId == null) {
            return false;
        }
        SourceEntity source = sourceRepository.findById(sourceId).orElse(null);
        return source != null && source.getType() == SourceType.TELEGRAM;
    }

    @Override
    public WarningDeliveryResult sendWarning(SubscriptionWarningDeliveryEntity delivery) {
        log.debug("Telegram warning send start: deliveryId={} orderId={} licenseId={} sourceId={}",
                delivery.getId(), delivery.getOrderId(), delivery.getLicenseId(), delivery.getSourceId());
        if (delivery.getOrderId() == null) {
            log.warn("Telegram warning send fail: deliveryId={} reason=orderId is null", delivery.getId());
            return WarningDeliveryResult.fail("Order id is null for delivery " + delivery.getId());
        }
        Optional<OperationExecutionEntity> orderCreationStage = operationExecutionRepository
                .findByEntityIdAndOperationTypeOrderByCreatedAtDesc(delivery.getOrderId(), OperationType.ORDER_CREATION)
                .stream()
                .findFirst();
        if (orderCreationStage.isEmpty() || orderCreationStage.get().getParentId() == null) {
            log.warn("Telegram warning send retry: deliveryId={} reason=order parent operation not found for orderId={}",
                    delivery.getId(), delivery.getOrderId());
            return WarningDeliveryResult.retry("Order operation binding not found for order " + delivery.getOrderId());
        }
        BigInteger parentId = orderCreationStage.get().getParentId();
        var bindingOpt = bindingRepository.findByOperationId(parentId);
        if (bindingOpt.isEmpty() || bindingOpt.get().getChatId() == null) {
            log.warn("Telegram warning send retry: deliveryId={} parentOpId={} reason=telegram binding not found",
                    delivery.getId(), parentId);
            return WarningDeliveryResult.retry("Telegram binding not found for parent operation " + parentId);
        }

        LicenseEntity license = delivery.getLicenseId() == null ? null : licenseRepository.findById(delivery.getLicenseId()).orElse(null);
        if (license == null) {
            log.warn("Telegram warning send fail: deliveryId={} reason=license not found licenseId={}",
                    delivery.getId(), delivery.getLicenseId());
            return WarningDeliveryResult.fail("License not found: " + delivery.getLicenseId());
        }
        boolean includeOffline = shouldIncludeOfflineForWarning(license);
        String keyPrefix = resolveKeyPrefixForWarning(license, includeOffline);
        String expiresAt = resolveExpiresAtForWarning(license, includeOffline);
        log.debug("Telegram warning payload: deliveryId={} parentOpId={} chatId={} controlMessageId={} keyPrefix={} expiresAt={}",
                delivery.getId(),
                parentId,
                bindingOpt.get().getChatId(),
                bindingOpt.get().getControlMessageId(),
                keyPrefix,
                expiresAt);

        String text = "Subscription warning\n"
                + "Order #" + delivery.getOrderId() + "\n"
                + "License: " + keyPrefix + "\n"
                + "Expires at: " + expiresAt;

        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(bindingOpt.get().getChatId().toString())
                    .text(text);
            if (bindingOpt.get().getControlMessageId() != null) {
                builder.replyToMessageId(bindingOpt.get().getControlMessageId()).allowSendingWithoutReply(true);
            }
            telegramSender.execute(builder.build());
            log.debug("Telegram warning sent: deliveryId={} chatId={} replyTo={}",
                    delivery.getId(),
                    bindingOpt.get().getChatId(),
                    bindingOpt.get().getControlMessageId());
            return WarningDeliveryResult.ok("sent");
        } catch (TelegramApiException ex) {
            log.warn("Failed to send subscription warning chatId={} orderId={} deliveryId={}: {}",
                    bindingOpt.get().getChatId(), delivery.getOrderId(), delivery.getId(), ex.getMessage());
            return WarningDeliveryResult.retry(ex.getMessage());
        }
    }

    private String resolveKeyPrefixForWarning(LicenseEntity license, boolean includeOffline) {
        if (license.getKey() == null) {
            return "-";
        }
        String onlinePrefix = prefix(license.getKey().getOnlineKey());
        String offlinePrefix = prefix(license.getKey().getOfflineKey());

        if (!includeOffline) {
            return "online=" + (onlinePrefix == null ? "-" : onlinePrefix);
        }

        if (onlinePrefix == null && offlinePrefix == null) {
            return "-";
        }
        return "online=" + (onlinePrefix == null ? "-" : onlinePrefix)
                + ", offline=" + (offlinePrefix == null ? "-" : offlinePrefix);
    }

    private String resolveExpiresAtForWarning(LicenseEntity license, boolean includeOffline) {
        Instant expires = includeOffline
                ? (license.getExpiresAt() != null
                    ? license.getExpiresAt()
                    : licenseExpirationResolver.resolveOfflineExpectedExpiration(license))
                : license.getExpiresAt();
        if (expires == null) {
            return "-";
        }
        return DTF.format(expires.atZone(ZoneId.systemDefault()));
    }

    private boolean shouldIncludeOfflineForWarning(LicenseEntity license) {
        Long orderItemId = license.getOrderItemId();
        if (orderItemId == null) {
            return false;
        }
        OrderItemEntity item = orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null || item.getKeyTypes() == null || item.getKeyTypes().isEmpty()) {
            return false;
        }
        return item.getKeyTypes().contains(KeyType.OFFLINE);
    }

    private String prefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }
}
