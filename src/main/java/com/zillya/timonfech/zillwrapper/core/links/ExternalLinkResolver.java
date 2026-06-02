package com.zillya.timonfech.zillwrapper.core.links;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class ExternalLinkResolver {

    @Value("${whiteAdminPanel.target}")
    private String whiteAdminBaseUrl;

    @Value("${whiteAdminPanel.orders.detailsPage}")
    private String whiteAdminOrderDetailsPage;

    @Value("${whiteAdminPanel.zab.detailsPage}")
    private String whiteAdminZabDetailsPage;

    @Value("${whiteAdminPanel.zis2.detailsPage}")
    private String whiteAdminZis2DetailsPage;

    public Optional<ExternalLink> resolveLicenseLink(LicenseEntity license) {
        if (license == null || license.getExternalId() == null) {
            return Optional.empty();
        }
        try {
            if (license.getKey() instanceof WhiteAdminKeyEntity) {
                boolean zab = isZab(license);
                String page = zab ? whiteAdminZabDetailsPage : whiteAdminZis2DetailsPage;
                ExternalLink.Source source = zab
                        ? ExternalLink.Source.WHITE_ADMIN_ZAB
                        : ExternalLink.Source.WHITE_ADMIN_ZIS2;
                String label = zab ? "Open in WhiteAdmin ZAB" : "Open in WhiteAdmin ZIS2";
                String url = buildUrl(whiteAdminBaseUrl, page, "id", String.valueOf(license.getExternalId()));
                return Optional.of(new ExternalLink(
                        ExternalLink.Kind.LICENSE,
                        source,
                        label,
                        url
                ));
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to resolve external license link id={} externalId={}: {}",
                    license.getId(), license.getExternalId(), ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ExternalLink> resolveOrderLink(OrderEntity order) {
        if (order == null || order.getWhiteAdminId() == null) {
            return Optional.empty();
        }
        try {
            String url = buildUrl(
                    whiteAdminBaseUrl,
                    whiteAdminOrderDetailsPage,
                    "id",
                    String.valueOf(order.getWhiteAdminId())
            );
            return Optional.of(new ExternalLink(
                    ExternalLink.Kind.ORDER,
                    ExternalLink.Source.WHITE_ADMIN_ORDER,
                    "Open order in WhiteAdmin",
                    url
            ));
        } catch (Exception ex) {
            log.warn("Failed to resolve external order link id={} whiteAdminId={}: {}",
                    order.getId(), order.getWhiteAdminId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean isZab(LicenseEntity license) {
        return Integer.valueOf(2).equals(license.getBrandId()) && Integer.valueOf(4).equals(license.getProductId());
    }

    private String buildUrl(String base, String path, String queryKey, String queryValue) {
        String normalizedBase = normalizeBase(base);
        String normalizedPath = path == null ? "" : path.trim();

        if (normalizedBase.endsWith("/") && normalizedPath.startsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        } else if (!normalizedBase.endsWith("/") && !normalizedPath.startsWith("/")) {
            normalizedBase = normalizedBase + "/";
        }

        String joined = normalizedBase + normalizedPath;
        String separator = joined.contains("?") ? "&" : "?";
        return joined + separator + queryKey + "=" + queryValue;
    }

    private String normalizeBase(String base) {
        String normalized = base == null ? "" : base.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        int queryIdx = normalized.indexOf('?');
        if (queryIdx >= 0) {
            normalized = normalized.substring(0, queryIdx);
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash > 0) {
            String tail = normalized.substring(lastSlash + 1).toLowerCase();
            if (tail.endsWith(".php")) {
                return normalized.substring(0, lastSlash + 1);
            }
        }
        return normalized;
    }
}
