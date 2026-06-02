package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotifyService {
    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailCommunicationService emailCommunicationService;
    private final EmailTemplateRoutingService templateRoutingService;
    private final ProductRegistry productRegistry;
    private final LicenseRepository licenseRepository;
    private final CryptoUtils cryptoUtils;

    public EmailSendResult sendOrderItems(OrderEntity order,
                                          List<OrderItemEntity> items,
                                          List<IArtifact> artifacts,
                                          Map<Long, List<IArtifact>> itemArtifacts,
                                          OperationType rootOperationType,
                                          Locale locale) {
        EmailSendResult result = new EmailSendResult();
        if (order == null || items == null || items.isEmpty()) {
            return result;
        }

        List<OrderDeliveryTargetEntity> emailTargets = order.getDeliveryTargets().stream()
                .filter(OrderDeliveryTargetEntity::isEnabled)
                .filter(t -> {
                    if (t.getContactMethod() == null) return false;
                    if (t.getContactMethod() instanceof EmailContact) return true;
                    return t.getContactMethod().getType() == ContactMethodType.EMAIL;
                })
                .toList();
        log.info("Email notify targets resolved orderId={} enabledEmailTargets={} totalTargets={} rootOpType={}",
                order.getId(),
                emailTargets.size(),
                order.getDeliveryTargets().size(),
                rootOperationType);

        for (OrderItemEntity item : items) {
            if (emailTargets.isEmpty()) {
                log.warn("Email notify failed itemId={} reason=no_email_targets orderId={}", item.getId(), order.getId());
                result.markFailed(item.getId(), "No enabled EMAIL delivery target for order " + order.getId());
                continue;
            }
            try {
                ProductInfo product = productRegistry.getProductById(item.getProductId()).orElse(null);
                if (product == null) {
                    result.markFailed(item.getId(), "Product not found for item " + item.getId());
                    continue;
                }

                List<LicenseEntity> licenses = licenseRepository.findByOrderItemId(item.getId());
                KeyType keyType = resolveKeyType(item);
                List<String> keys = licenses.stream()
                        .map(license -> extractKey(item, license, keyType))
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
                List<IArtifact> itemAttachments = resolveAttachments(item, artifacts, itemArtifacts);
                boolean excelRequested = item.getOutputTypes() != null && item.getOutputTypes().contains(OutputType.EXCEL);
                boolean textAllowed = item.getOutputTypes() != null && item.getOutputTypes().contains(OutputType.TEXT);
                if (excelRequested && itemAttachments.isEmpty() && !textAllowed) {
                    result.markFailed(item.getId(), "Excel attachment missing and TEXT fallback is unavailable for item " + item.getId());
                    log.warn("Email notify failed itemId={} reason=excel_attachment_missing_without_text_fallback orderId={}",
                            item.getId(),
                            order.getId());
                    continue;
                }
                if (excelRequested && itemAttachments.isEmpty() && textAllowed) {
                    log.warn("Email notify fallback to TEXT because EXCEL attachment is missing orderId={} itemId={}",
                            order.getId(),
                            item.getId());
                }
                log.info("Email key selection orderId={} itemId={} itemKeyTypes={} effectiveKeyType={} keysCount={}",
                        order.getId(),
                        item.getId(),
                        item.getKeyTypes(),
                        keyType,
                        keys.size());
                boolean sentAtLeastOnce = false;

                for (OrderDeliveryTargetEntity target : emailTargets) {
                    String email = extractEmail(target);
                    if (email == null) {
                        log.warn("Email notify failed itemId={} reason=target_email_not_resolved targetId={} orderId={}",
                                item.getId(),
                                target.getId(),
                                order.getId());
                        result.markFailed(item.getId(), "Unable to resolve email target for order " + order.getId());
                        continue;
                    }
                    ResolvedEmailTemplate resolvedTemplate = templateRoutingService.resolve(rootOperationType, target, item);
                    Map<String, Object> model = buildModel(
                            order,
                            item,
                            product,
                            keys,
                            keyType,
                            itemAttachments,
                            locale,
                            resolvedTemplate
                    );
                    log.info("Email notify sending orderId={} itemId={} targetId={} attachments={} template={} routingRule={}",
                            order.getId(),
                            item.getId(),
                            target.getId(),
                            itemAttachments == null ? 0 : itemAttachments.size(),
                            resolvedTemplate.template(),
                            resolvedTemplate.ruleId());
                    emailCommunicationService.send(email, resolvedTemplate, model, locale, itemAttachments);
                    sentAtLeastOnce = true;
                }
                if (sentAtLeastOnce) {
                    result.markDelivered(item.getId());
                } else {
                    result.markFailed(item.getId(), "No valid email delivery target for item " + item.getId());
                }
            } catch (Exception ex) {
                log.error("Failed to send email for order {} item {}: {}", order.getId(), item.getId(), ex.getMessage(), ex);
                result.markFailed(item.getId(), ex.getMessage());
            }
        }
        return result;
    }

    private Map<String, Object> buildModel(OrderEntity order,
                                           OrderItemEntity item,
                                           ProductInfo product,
                                           List<String> keys,
                                           KeyType keyType,
                                           List<IArtifact> itemAttachments,
                                           Locale locale,
                                           ResolvedEmailTemplate resolvedTemplate) {
        Map<String, Object> model = new HashMap<>();
        String productName = resolveEmailProductDisplayName(product, locale);
        model.put("productName", productName);
        Map<String, String> props = product.getProperties(locale, keyType);
        props = enrichDownloadLinks(product, props);
        model.put("props", props != null ? props : Collections.emptyMap());
        log.info("Email model resolved: orderId={}, itemId={}, product={}/{}, keyType={}, propsKeys={}, hasExcelOutput={}",
                order == null ? null : order.getId(),
                item == null ? null : item.getId(),
                product.brandId(),
                product.productId(),
                keyType,
                props == null ? List.of() : props.keySet(),
                item != null && item.getOutputTypes() != null && item.getOutputTypes().contains(OutputType.EXCEL));
        model.put("activationFlow", resolveActivationFlow(product));
        model.put("keyType", keyType.name());
        model.put("keys", keys);
        model.put("subjectCount", keys == null || keys.isEmpty()
                ? (item.getCount() == null || item.getCount() <= 0 ? 1 : item.getCount())
                : keys.size());
        model.put("subjectOfflineRequested",
                item.getKeyTypes() != null && item.getKeyTypes().contains(KeyType.OFFLINE));
        model.put("pcCount", item.getPcPerLicense() != null ? item.getPcPerLicense().toString() : "1");
        model.put("durationText", resolveDurationText(item.getBusinessPeriod(), locale));
        boolean hasAttachment = item.getOutputTypes() != null && item.getOutputTypes().contains(OutputType.EXCEL)
                && itemAttachments != null && !itemAttachments.isEmpty();
        model.put("hasAttachment", hasAttachment);
        return model;
    }

    private String resolveActivationFlow(ProductInfo product) {
        int p = product.productId();
        if (p == 3) return "ZIS2";
        if (p == 4) return "OFFLINE";
        if (p == 2) return "ANDROID";
        return "ZIS3";
    }

    private Map<String, String> enrichDownloadLinks(ProductInfo product, Map<String, String> filteredProps) {
        Map<String, String> result = new HashMap<>();
        if (filteredProps != null) {
            result.putAll(filteredProps);
        }
        Map<String, String> all = product.properties();
        if (all == null || all.isEmpty()) {
            return result;
        }
        copyIfAbsent(result, all, "uk_online_direct_link");
        copyIfAbsent(result, all, "ru_online_direct_link");
        copyIfAbsent(result, all, "en_online_direct_link");
        copyIfAbsent(result, all, "offline_direct_link");
        copyIfAbsent(result, all, "google_play_link");
        copyIfAbsent(result, all, "direct_link");
        return result;
    }

    private void copyIfAbsent(Map<String, String> target, Map<String, String> source, String key) {
        if (!target.containsKey(key) && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String extractEmail(OrderDeliveryTargetEntity target) {
        if (!(target.getContactMethod() instanceof EmailContact emailContact)) {
            return null;
        }
        String candidate = emailContact.getPlainValue();
        if (candidate == null || candidate.isBlank()) {
            candidate = decryptEmail(emailContact.getEncryptedValue());
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String email = candidate.trim();
        if (!SIMPLE_EMAIL_PATTERN.matcher(email).matches()) {
            return null;
        }
        return email;
    }

    private String decryptEmail(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            return cryptoUtils.decryptFromBase64(encryptedValue);
        } catch (Exception ex) {
            // Backward compatibility: in legacy flows encryptedValue may already contain plain e-mail.
            return encryptedValue;
        }
    }

    private String extractKey(OrderItemEntity item, LicenseEntity license, KeyType fallbackType) {
        if (license.getKey() == null) return null;
        List<KeyType> requestedTypes = item != null && item.getKeyTypes() != null && !item.getKeyTypes().isEmpty()
                ? item.getKeyTypes()
                : List.of(fallbackType == null ? KeyType.ONLINE : fallbackType);
        if (requestedTypes.contains(KeyType.ONLINE) && requestedTypes.contains(KeyType.OFFLINE)) {
            String online = normalizeOnlineKey(license.getKey().getOnlineKey());
            String offline = normalizeOfflineKey(license);
            if (online != null && offline != null) {
                return "ONLINE: " + online + " | OFFLINE: " + offline;
            }
            if (online != null) {
                return "ONLINE: " + online;
            }
            if (offline != null) {
                return "OFFLINE: " + offline;
            }
            return null;
        }
        if (requestedTypes.contains(KeyType.OFFLINE)) {
            return normalizeOfflineKey(license);
        }
        return normalizeOnlineKey(license.getKey().getOnlineKey());
    }

    private String normalizeOnlineKey(String onlineKey) {
        if (onlineKey == null || onlineKey.isBlank()) {
            return null;
        }
        return onlineKey.trim();
    }

    private String normalizeOfflineKey(LicenseEntity license) {
        if (license == null || license.getKey() == null) {
            return null;
        }
        String offlineKey = license.getKey().getOfflineKey();
        if (offlineKey == null || offlineKey.isBlank()) {
            return null;
        }
        if (!(license.getKey() instanceof WhiteAdminKeyEntity)) {
            return offlineKey.trim();
        }
        return KeyMarkersUtils.addMarkers(KeyMarkersUtils.removeMarkers(offlineKey));
    }

    private List<IArtifact> resolveAttachments(OrderItemEntity item,
                                               List<IArtifact> artifacts,
                                               Map<Long, List<IArtifact>> itemArtifacts) {
        if (item != null && item.getId() != null && itemArtifacts != null && !itemArtifacts.isEmpty()) {
            List<IArtifact> mapped = itemArtifacts.get(item.getId());
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
        }
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        if (item.getOutputTypes() == null || !item.getOutputTypes().contains(OutputType.EXCEL)) {
            return List.of();
        }
        if (item.getId() == null) {
            return List.of();
        }
        return List.of();
    }

    private KeyType resolveKeyType(OrderItemEntity item) {
        if (item.getKeyTypes() != null && !item.getKeyTypes().isEmpty()) {
            return item.getKeyTypes().getFirst();
        }
        return KeyType.ONLINE;
    }

    private String resolveEmailProductDisplayName(ProductInfo product, Locale locale) {
        boolean zab = isZab(product);
        boolean withVersion = !zab;
        Locale displayLocale = zab ? (locale == null ? Locale.forLanguageTag("uk") : locale) : Locale.ENGLISH;
        try {
            String resolved = product.getName(displayLocale, true, withVersion);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return product.brandId() + "/" + product.productId();
    }

    private boolean isZab(ProductInfo product) {
        return product.productId() == 4 && product.brandId() == 2;
    }

    private String resolveDurationText(BusinessPeriod period, Locale locale) {
        if (period == null || period.amount() <= 0 || period.unit() == null) {
            return "1 year";
        }
        String lang = locale == null ? "en" : locale.getLanguage().toLowerCase(Locale.ROOT);
        int amount = period.amount();
        if ("uk".equals(lang)) {
            return amount + " " + ukrainianUnitWord(period.unit(), amount);
        }
        return amount + " " + englishUnitWord(period.unit(), amount);
    }

    private String englishUnitWord(com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit unit, int amount) {
        return switch (unit) {
            case DAY -> amount == 1 ? "day" : "days";
            case MONTH -> amount == 1 ? "month" : "months";
            case YEAR -> amount == 1 ? "year" : "years";
        };
    }

    private String ukrainianUnitWord(com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit unit, int amount) {
        int mod10 = amount % 10;
        int mod100 = amount % 100;
        boolean one = mod10 == 1 && mod100 != 11;
        boolean few = mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20);
        return switch (unit) {
            case DAY -> one ? "день" : (few ? "дні" : "днів");
            case MONTH -> one ? "місяць" : (few ? "місяці" : "місяців");
            case YEAR -> one ? "рік" : (few ? "роки" : "років");
        };
    }
}
