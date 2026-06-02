package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.communication.KeyPreviewFormatter;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramTextRenderer;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.links.ExternalLinkResolver;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LicenseViewRenderer implements EntityViewRenderer<LicenseEntity> {
    private final ProductRegistry productRegistry;
    private final KeyPreviewFormatter keyPreviewFormatter;
    private final OrderItemRepository orderItemRepository;
    private final ExternalLinkResolver externalLinkResolver;
    private final TelegramTextRenderer telegramTextRenderer;
    @Value("${telegram.render.html.cards.enabled:false}")
    private boolean htmlCardsEnabled;

    @Override
    public SearchEntityType entityType() {
        return SearchEntityType.LICENSE;
    }

    @Override
    public Class<LicenseEntity> modelType() {
        return LicenseEntity.class;
    }

    @Override
    public String render(LicenseEntity l) {
        String product = resolveProductShortName(l);
        var sourceLink = externalLinkResolver.resolveLicenseLink(l);
        String linkValue = sourceLink.map(link -> link.url()).orElse("-");
        if (!htmlCardsEnabled) {
            return "License #" + l.getId()
                    + "\nexternalId=" + (l.getExternalId() == null ? "-" : l.getExternalId())
                    + "\norderId=" + (l.getOrderId() == null ? "-" : l.getOrderId())
                    + "\nproduct=" + product
                    + "\nstatus=" + l.getStatus()
                    + "\nlink=" + linkValue
                    + "\n" + renderRequestedTypes(l)
                    + "\n" + renderRequestedKeyLine(l);
        }
        return telegramTextRenderer.bold("License #" + safe(l.getId()))
                + "\nexternalId=" + telegramTextRenderer.code(safeOrDash(l.getExternalId()))
                + "\norderId=" + telegramTextRenderer.code(safeOrDash(l.getOrderId()))
                + "\nproduct=" + telegramTextRenderer.bold(product)
                + "\nstatus=" + telegramTextRenderer.bold(String.valueOf(l.getStatus()))
                + "\nlink=" + sourceLink
                .map(link -> telegramTextRenderer.link("Open source", link.url()))
                .orElse("-")
                + "\n" + renderRequestedTypes(l)
                + "\n" + renderRequestedKeyLine(l);
    }

    @Override
    public Long internalId(LicenseEntity entity) {
        return entity.getId();
    }

    private String resolveProductShortName(LicenseEntity license) {
        Integer productId = license.getProductId();
        Integer brandId = license.getBrandId();
        if (productId == null && brandId == null) {
            return "-";
        }
        if (productId != null && brandId == null) {
            return "productId=" + productId;
        }
        if (productId == null) {
            return "brandId=" + brandId;
        }
        ProductInfo info = productRegistry.getProductById(productId)
                .filter(p -> p.brandId() == brandId)
                .orElse(null);
        if (info == null) {
            return brandId + "/" + productId;
        }
        try {
            return info.getName(Locale.ENGLISH, false, true);
        } catch (Exception ignored) {
            return brandId + "/" + productId;
        }
    }

    private String renderRequestedKeyLine(LicenseEntity license) {
        String raw = keyPreviewFormatter.renderForLicense(license, List.of(), List.of());
        String on = extract(raw, "ON=");
        String off = extract(raw, "OFF=");
        Set<KeyType> requested = resolveRequestedTypesSet(license);

        String renderedOn = requested.contains(KeyType.ONLINE)
                ? htmlCardsEnabled ? telegramTextRenderer.bold("ON=" + on) : "[ON=" + on + "]"
                : "ON=" + on;
        String renderedOff = requested.contains(KeyType.OFFLINE)
                ? htmlCardsEnabled ? telegramTextRenderer.bold("OFF=" + off) : "[OFF=" + off + "]"
                : "OFF=" + off;
        return "key: " + renderedOn + ", " + renderedOff;
    }

    private String renderRequestedTypes(LicenseEntity license) {
        if (license == null || license.getOrderItemId() == null) {
            return "requested=-";
        }
        OrderItemEntity item = orderItemRepository.findById(license.getOrderItemId()).orElse(null);
        if (item == null || item.getKeyTypes() == null || item.getKeyTypes().isEmpty()) {
            return "requested=-";
        }
        String requested = item.getKeyTypes().stream()
                .map(type -> type == KeyType.ONLINE ? "ONLINE" : "OFFLINE")
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
        return "requested=" + (requested.isBlank() ? "-" : requested);
    }

    private Set<KeyType> resolveRequestedTypesSet(LicenseEntity license) {
        if (license == null || license.getOrderItemId() == null) {
            return Set.of();
        }
        OrderItemEntity item = orderItemRepository.findById(license.getOrderItemId()).orElse(null);
        if (item == null || item.getKeyTypes() == null || item.getKeyTypes().isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(item.getKeyTypes());
    }

    private String extract(String line, String marker) {
        int idx = line.indexOf(marker);
        if (idx < 0) {
            return "-";
        }
        String tail = line.substring(idx + marker.length()).trim();
        int comma = tail.indexOf(',');
        return comma >= 0 ? tail.substring(0, comma).trim() : tail;
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String safeOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

}
