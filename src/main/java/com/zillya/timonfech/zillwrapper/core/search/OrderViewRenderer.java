package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.communication.KeyPreviewFormatter;
import com.zillya.timonfech.zillwrapper.core.communication.TelegramTextRenderer;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.links.ExternalLink;
import com.zillya.timonfech.zillwrapper.core.links.ExternalLinkResolver;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderViewRenderer implements EntityViewRenderer<OrderEntity> {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private final LicenseRepository licenseRepository;
    private final OrderItemRepository orderItemRepository;
    private final KeyPreviewFormatter keyPreviewFormatter;
    private final ExternalLinkResolver externalLinkResolver;
    private final TelegramTextRenderer telegramTextRenderer;
    @Value("${telegram.render.html.cards.enabled:false}")
    private boolean htmlCardsEnabled;

    @Override
    public SearchEntityType entityType() {
        return SearchEntityType.ORDER;
    }

    @Override
    public Class<OrderEntity> modelType() {
        return OrderEntity.class;
    }

    @Override
    public String render(OrderEntity o) {
        List<LicenseEntity> licenses = licenseRepository.findByOrderId(o.getId());
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdOrderByIdAsc(o.getId());
        String chars = items.stream()
                .map(this::compactItemCharacteristics)
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining("; "));
        String keyPreview = keyPreviewFormatter.renderForOrder(licenses, items);
        var sourceLink = externalLinkResolver.resolveOrderLink(o);
        String sourceLinkValue = sourceLink.map(ExternalLink::url).orElse("-");
        if (!htmlCardsEnabled) {
            return "Order #" + o.getId()
                    + "\nportalId=" + (o.getPortalId() == null ? "-" : o.getPortalId())
                    + "\nwhiteAdminId=" + (o.getWhiteAdminId() == null ? "-" : o.getWhiteAdminId())
                    + "\nlink=" + sourceLinkValue
                    + "\ncomment=" + (o.getUserComment() == null ? "-" : o.getUserComment())
                    + "\nkeysTotal=" + licenses.size()
                    + "\nitems=" + (chars.isBlank() ? "-" : chars)
                    + "\n" + keyPreview;
        }

        return telegramTextRenderer.bold("Order #" + safe(o.getId()))
                + "\nwhiteAdminId=" + telegramTextRenderer.code(safe(o.getWhiteAdminId()))
                + "\nportalId=" + telegramTextRenderer.code(safe(o.getPortalId()))
                + "\nkeysTotal=" + telegramTextRenderer.bold(String.valueOf(licenses.size()))
                + "\nlink=" + sourceLink
                .map(link -> telegramTextRenderer.link("Open source", link.url()))
                .orElse("-")
                + "\ncomment=" + telegramTextRenderer.escapeHtml(o.getUserComment() == null ? "-" : o.getUserComment())
                + "\nitems=" + telegramTextRenderer.escapeHtml(chars.isBlank() ? "-" : chars)
                + "\n" + telegramTextRenderer.escapeHtml(keyPreview);
    }

    @Override
    public Long internalId(OrderEntity entity) {
        return entity.getId();
    }

    private String compactItemCharacteristics(OrderItemEntity item) {
        String pcs = item.getPcPerLicense() == null ? "-" : item.getPcPerLicense().toString();
        String term = "-";
        if (item.getPeriodAmount() != null && item.getPeriodUnit() != null) {
            term = item.getPeriodAmount() + shortUnit(item.getPeriodUnit().name());
        }
        List<LicenseEntity> itemLicenses = licenseRepository.findByOrderItemId(item.getId());
        String exp = itemLicenses.stream()
                .map(LicenseEntity::getExpiresAt)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(TS_FMT::format)
                .orElse("-");
        return "item" + item.getId() + ": " + pcs + "pc, term " + term + ", exp " + exp;
    }

    private String shortUnit(String unit) {
        String lower = unit.toLowerCase(Locale.ROOT);
        if (lower.startsWith("day")) return "d";
        if (lower.startsWith("month")) return "m";
        if (lower.startsWith("year")) return "y";
        if (lower.startsWith("hour")) return "h";
        return lower;
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

}
