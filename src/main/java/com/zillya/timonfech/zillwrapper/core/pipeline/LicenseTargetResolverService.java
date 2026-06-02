package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.search.KeySearchNormalizer;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LicenseTargetResolverService {
    private final LicenseRepository licenseRepository;
    private final OrderRepository orderRepository;
    private final KeySearchNormalizer keySearchNormalizer;

    public List<LicenseEntity> resolveTargets(OrderOperationContext ctx) {
        Set<Long> ids = new HashSet<>();
        List<LicenseEntity> out = new ArrayList<>();

        if (ctx.getOrderId() != null) {
            addOrderLicensesByOrderId(ctx.getOrderId(), out, ids);
        }

        if (ctx.getPortalId() != null) {
            addLicense(licenseRepository.findByExternalId(ctx.getPortalId()).orElse(null), out, ids);
            addOrderLicensesByAnyRef(ctx.getPortalId(), out, ids);
        }
        if (ctx.getWhiteAdminId() != null) {
            addOrderLicensesByWhiteAdminId(ctx.getWhiteAdminId(), out, ids);
        }
        if (ctx.getUserComment() != null && !ctx.getUserComment().isBlank()) {
            addOrderLicensesByUserComment(ctx.getUserComment(), out, ids);
        }

        String payload = ctx.getCommandPayload();
        if (payload == null || payload.isBlank()) {
            return out;
        }
        payload = payload.trim();

        List<String> tokens = Arrays.stream(payload.split("\\s+"))
                .filter(t -> t != null && !t.isBlank())
                .toList();
        for (String token : tokens) {
            String[] kv = token.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String k = kv[0].trim().toLowerCase();
            String v = kv[1].trim();
            if (k.equals("lid")) {
                for (String rawId : v.split(",")) {
                    Long lid = tryParseLong(rawId.trim());
                    if (lid != null) {
                        addLicense(licenseRepository.findById(lid).orElse(null), out, ids);
                    }
                }
            } else if (k.equals("lex")) {
                Long lex = tryParseLong(v);
                if (lex != null) {
                    addLicense(licenseRepository.findByExternalId(lex).orElse(null), out, ids);
                }
            } else if (k.equals("kid")) {
                Long kid = tryParseLong(v);
                if (kid != null) {
                    addLicense(licenseRepository.findByKey_Id(kid).orElse(null), out, ids);
                }
            } else if (k.equals("kon")) {
                String normalized = keySearchNormalizer.normalizeForSearch(v);
                if (normalized != null) {
                    boolean exactMatched = licenseRepository.findFirstByKey_OnlineKeyIgnoreCase(normalized)
                            .map(l -> {
                                addLicense(l, out, ids);
                                return true;
                            })
                            .orElse(false);
                    if (!keySearchNormalizer.isFullKey(normalized) || !exactMatched) {
                        licenseRepository.findAllByOnlineOrOfflineContainsCi(normalized).forEach(l -> addLicense(l, out, ids));
                    }
                }
            } else if (k.equals("kof")) {
                String normalized = keySearchNormalizer.normalizeForSearch(v);
                if (normalized != null) {
                    boolean exactMatched = licenseRepository.findFirstByKey_OfflineKeyIgnoreCase(normalized)
                            .map(l -> {
                                addLicense(l, out, ids);
                                return true;
                            })
                            .orElse(false);
                    if (!keySearchNormalizer.isFullKey(normalized) || !exactMatched) {
                        licenseRepository.findAllByOnlineOrOfflineContainsCi(normalized).forEach(l -> addLicense(l, out, ids));
                    }
                }
            }
        }

        Long numeric = tryParseLong(payload);
        if (numeric != null) {
            addLicense(licenseRepository.findByExternalId(numeric).orElse(null), out, ids);
            addLicense(licenseRepository.findByKey_Id(numeric).orElse(null), out, ids);
            addOrderLicensesByAnyRef(numeric, out, ids);
            return out;
        }

        String normalizedKey = keySearchNormalizer.normalizeForSearch(payload);
        if (normalizedKey != null) {
            boolean exactMatched = false;
            if (licenseRepository.findFirstByKey_OnlineKeyIgnoreCase(normalizedKey)
                    .map(l -> {
                        addLicense(l, out, ids);
                        return true;
                    })
                    .orElse(false)) {
                exactMatched = true;
            }
            if (licenseRepository.findFirstByKey_OfflineKeyIgnoreCase(normalizedKey)
                    .map(l -> {
                        addLicense(l, out, ids);
                        return true;
                    })
                    .orElse(false)) {
                exactMatched = true;
            }
            if (!keySearchNormalizer.isFullKey(normalizedKey) || !exactMatched) {
                for (LicenseEntity license : licenseRepository.findAllByOnlineOrOfflineContainsCi(normalizedKey)) {
                    addLicense(license, out, ids);
                }
            }
        }

        addOrderLicensesByUserComment(payload, out, ids);
        return out;
    }

    private void addOrderLicensesByAnyRef(Long ref, List<LicenseEntity> out, Set<Long> ids) {
        if (ref == null) {
            return;
        }
        addOrderLicensesByOrderId(ref, out, ids);
        for (var order : orderRepository.findAllByPortalId(ref)) {
            addOrderLicensesByOrderId(order.getId(), out, ids);
        }
        addOrderLicensesByWhiteAdminId(ref, out, ids);
    }

    private void addOrderLicensesByOrderId(Long orderId, List<LicenseEntity> out, Set<Long> ids) {
        if (orderId == null) {
            return;
        }
        for (LicenseEntity license : licenseRepository.findByOrderId(orderId)) {
            addLicense(license, out, ids);
        }
    }

    private void addOrderLicensesByWhiteAdminId(Long whiteAdminId, List<LicenseEntity> out, Set<Long> ids) {
        if (whiteAdminId == null) {
            return;
        }
        for (var order : orderRepository.findAllByWhiteAdminId(whiteAdminId)) {
            addOrderLicensesByOrderId(order.getId(), out, ids);
        }
    }

    private void addOrderLicensesByUserComment(String comment, List<LicenseEntity> out, Set<Long> ids) {
        if (comment == null || comment.isBlank()) {
            return;
        }
        for (var order : orderRepository.findAllByUserCommentNormalized(comment)) {
            addOrderLicensesByOrderId(order.getId(), out, ids);
        }
    }

    private void addLicense(LicenseEntity license, List<LicenseEntity> out, Set<Long> ids) {
        if (license == null || license.getId() == null || ids.contains(license.getId())) {
            return;
        }
        ids.add(license.getId());
        out.add(license);
    }

    private Long tryParseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

}
