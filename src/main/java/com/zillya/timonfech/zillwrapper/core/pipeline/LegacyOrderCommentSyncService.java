package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.apis.enrichers.dto.LegalEntityInfo;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyOrderCommentSyncService {

    private final AbstractWhiteAdminClient client;
    private final LicenseRepository licenseRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final LegacyCommentLicenseParser commentLicenseParser;

    @Value("${whiteAdminPanel.orders.commentSavePage:/payment/admin_page.php}")
    private String targetUrl;
    @Value("${whiteAdminPanel.orders.detailsPage:admin_page_popup.php}")
    private String detailsUrl;
    @Value("${whiteAdminPanel.legacySync.maxCommentLength:3000}")
    private int maxCommentLength;
    @Value("${whiteAdminPanel.legacySync.compactPrefixLength:8}")
    private int compactPrefixLength;

    public LegacySyncOutcome sync(OrderEntity order, LegacySyncDecision decision) {
        if (order == null || order.getId() == null || order.getWhiteAdminId() == null) {
            return LegacySyncOutcome.warning("SKIPPED_MISSING_ORDER_OR_WHITEADMIN_ID");
        }
        boolean waSaved = false;
        boolean localSaved = false;
        boolean moved = false;
        String warning = decision.reason();

        if (decision.appendComment()) {
            List<KeyLine> payloadLines = buildCommentLines(order.getId(), false);
            CommentPayload payload = buildCommentPayload(payloadLines);
            WhiteAdminOrderDetails waDetails = readOrderDetailsFromWhiteAdmin(order.getWhiteAdminId());
            String existingWaComment = waDetails.comment();
            if (existingWaComment == null) {
                existingWaComment = normalizeLine(order.getUserComment());
            }
            LegacyCommentLicenseParser.ParseResult parsed = commentLicenseParser.parse(existingWaComment, "whiteadmin");
            relinkLicensesByCommentTokens(order, parsed.tokens());
            if (!parsed.unmatchedChunks().isEmpty()) {
                log.debug("Legacy sync comment parser unmatched chunks count={} whiteAdminId={}",
                        parsed.unmatchedChunks().size(),
                        order.getWhiteAdminId());
            }

            String finalComment = composeFinalComment(existingWaComment, payloadLines, parsed.normalizedKeys());
            if (finalComment != null) {
                saveCommentAtWhiteAdmin(order.getWhiteAdminId(), finalComment, payload.mode(), order.getId());
                waSaved = true;
                try {
                    if (waDetails.legalEntityInfoJson() != null && !waDetails.legalEntityInfoJson().isBlank()) {
                        int updated = orderRepository.updateLegalEntityInfoJsonById(order.getId(), waDetails.legalEntityInfoJson());
                        localSaved = updated > 0;
                    }
                } catch (Exception ex) {
                    warning = mergeWarning(warning, "LOCAL_COMMENT_PERSIST_FAILED: " + ex.getMessage());
                }
            }
        }

        if (decision.moveToProcessed() && waSaved) {
            moveToProcessed(order.getWhiteAdminId());
            moved = true;
        }

        return new LegacySyncOutcome(waSaved, localSaved, moved, warning);
    }

    private List<KeyLine> buildCommentLines(Long orderId, boolean compact) {
        List<LicenseEntity> licenses = licenseRepository.findByOrderId(orderId);
        List<KeyLine> fullLines = licenses.stream()
                .filter(license -> license.getKey() != null)
                .map(license -> {
                    String online = normalize(license.getKey().getOnlineKey());
                    if (online == null) {
                        return null;
                    }
                    boolean offline = isOfflineOrdered(license);
                    return new KeyLine(online, offline);
                })
                .filter(line -> line != null)
                .toList();
        if (!compact) {
            return fullLines;
        }
        return fullLines.stream()
                .map(line -> new KeyLine(prefix(line.online(), compactPrefixLength), line.offline()))
                .collect(Collectors.toList());
    }

    private CommentPayload buildCommentPayload(List<KeyLine> fullLines) {
        String fullPayload = joinLines(fullLines, false);
        if (fullPayload.length() <= maxCommentLength) {
            return new CommentPayload(fullPayload, "full");
        }

        List<KeyLine> compactLines = buildCommentLinesFrom(fullLines, true);
        String compactPayload = joinLines(compactLines, false);
        if (compactPayload.length() <= maxCommentLength) {
            return new CommentPayload(compactPayload, "compact");
        }

        return new CommentPayload(compactPayload.substring(0, Math.max(0, maxCommentLength)), "truncated");
    }

    private List<KeyLine> buildCommentLinesFrom(List<KeyLine> lines, boolean compact) {
        if (!compact) {
            return lines;
        }
        return lines.stream()
                .map(line -> new KeyLine(prefix(line.online(), compactPrefixLength), line.offline()))
                .toList();
    }

    private String joinLines(List<KeyLine> lines, boolean compact) {
        StringBuilder sb = new StringBuilder();
        for (KeyLine line : lines) {
            sb.append(line.online());
            if (line.offline()) {
                sb.append("+off");
            }
            sb.append(",\n");
        }
        return sb.toString();
    }

    private String prefix(String value, int length) {
        if (value == null) {
            return null;
        }
        int safeLen = Math.max(1, length);
        return value.substring(0, Math.min(safeLen, value.length()));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isBlank() ? null : v;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = KeyMarkersUtils.removeMarkers(value).replaceAll("\\s+", "").trim();
        return cleaned.isBlank() ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private boolean isOfflineOrdered(LicenseEntity license) {
        if (license == null || license.getOrderItemId() == null) {
            return false;
        }
        return orderItemRepository.findById(license.getOrderItemId())
                .map(item -> {
                    List<KeyType> keyTypes = item.getKeyTypes();
                    if (keyTypes == null || keyTypes.isEmpty()) {
                        return false;
                    }
                    return keyTypes.contains(KeyType.OFFLINE) && !keyTypes.contains(KeyType.ONLINE);
                })
                .orElse(false);
    }

    public void moveToProcessed(Long whiteAdminId) {
        performSimpleAction(whiteAdminId, "move_to_processed");
    }

    public void moveToPayed(Long whiteAdminId) {
        performSimpleAction(whiteAdminId, "move_to_payed");
    }

    private void performSimpleAction(Long whiteAdminId, String action) {
        if (whiteAdminId == null) {
            return;
        }
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(whiteAdminId)));
        params.add(new BasicNameValuePair("do", action));
        try {
            client.loadDocument(targetUrl, params, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute WhiteAdmin action " + action, e);
        }
    }

    private void saveCommentAtWhiteAdmin(Long whiteAdminId, String finalComment, String mode, Long orderId) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(whiteAdminId)));
        params.add(new BasicNameValuePair("do", "save_comment"));
        params.add(new BasicNameValuePair("comment", finalComment));
        log.info("Legacy sync comment payload mode={} orderId={} whiteAdminId={} length={}",
                mode,
                orderId,
                whiteAdminId,
                finalComment.length());
        try {
            client.loadDocument(targetUrl, params, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to sync order comments to WhiteAdmin", e);
        }
    }

    private WhiteAdminOrderDetails readOrderDetailsFromWhiteAdmin(Long whiteAdminId) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", String.valueOf(whiteAdminId)));
        try {
            Document doc = client.loadDocument(detailsUrl, params, false);
            Element commentEl = doc.selectFirst("textarea#admin_comment");
            String comment = commentEl == null ? null : normalizeLine(commentEl.val());
            String legalEntityInfoJson = tryParseLegalEntityInfoJson(doc, whiteAdminId);
            return new WhiteAdminOrderDetails(comment, legalEntityInfoJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read order comment from WhiteAdmin", e);
        }
    }

    private String tryParseLegalEntityInfoJson(Document doc, Long whiteAdminId) {
        try {
            Elements values = doc.select("html body fieldset table tbody tr td");
            if (values.size() < 22) {
                log.debug("Legacy sync: legal section not found for whiteAdminId={}", whiteAdminId);
                return null;
            }
            String tin = normalizeLine(values.get(18).text());
            String companyName = normalizeLine(values.get(19).text());
            String physicalAddress = normalizeLine(values.get(20).text());
            String legalAddress = normalizeLine(values.get(21).text());

            boolean hasAny = tin != null || companyName != null || physicalAddress != null || legalAddress != null;
            if (!hasAny) {
                return null;
            }
            return objectMapper.writeValueAsString(new LegalEntityInfo(tin, companyName, physicalAddress, legalAddress));
        } catch (Exception ex) {
            log.warn("Legacy sync: failed to parse legal section for whiteAdminId={}: {}", whiteAdminId, ex.getMessage());
            return null;
        }
    }

    private String appendComment(String existing, String addition) {
        String normalizedAddition = normalizeLine(addition);
        if (normalizedAddition == null) {
            return normalizeLine(existing);
        }
        String normalizedExisting = normalizeLine(existing);
        if (normalizedExisting == null) {
            return normalizedAddition;
        }
        return normalizedExisting + "\n" + normalizedAddition;
    }

    private String composeFinalComment(String existingWaComment, List<KeyLine> payloadLines, Set<String> existingNormalizedKeys) {
        Set<String> allExisting = existingNormalizedKeys == null
                ? new HashSet<>()
                : new HashSet<>(existingNormalizedKeys);

        List<KeyLine> newLines = payloadLines.stream()
                .filter(line -> {
                    String key = normalizeKey(line.online());
                    if (key == null) {
                        return false;
                    }
                    if (allExisting.contains(key)) {
                        return false;
                    }
                    allExisting.add(key);
                    return true;
                })
                .toList();

        String addition = joinLines(newLines, false);
        return appendComment(existingWaComment, addition);
    }

    private void relinkLicensesByCommentTokens(OrderEntity order, List<LegacyCommentLicenseParser.Token> tokens) {
        if (order == null || order.getId() == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        List<LicenseEntity> changed = new ArrayList<>();
        for (LegacyCommentLicenseParser.Token token : tokens) {
            String key = normalizeKey(token.key());
            if (key == null) {
                continue;
            }
            LicenseEntity license = resolveLicenseByToken(key).orElse(null);
            if (license == null) {
                log.debug("Legacy sync token not found in DB key={} whiteAdminId={}", key, order.getWhiteAdminId());
                continue;
            }
            if (order.getId().equals(license.getOrderId())) {
                continue;
            }
            license.setOrderId(order.getId());
            changed.add(license);
        }
        if (!changed.isEmpty()) {
            licenseRepository.saveAll(changed);
            log.info("Legacy sync relinked licenses count={} orderId={} whiteAdminId={}",
                    changed.size(), order.getId(), order.getWhiteAdminId());
        }
    }

    private java.util.Optional<LicenseEntity> resolveLicenseByToken(String token) {
        List<LicenseEntity> candidates = licenseRepository.findAllByOnlineOrOfflineContainsCi(token);
        return candidates.stream()
                .filter(l -> l.getKey() != null)
                .filter(l -> token.equals(normalizeKey(l.getKey().getOnlineKey()))
                        || token.equals(normalizeKey(l.getKey().getOfflineKey())))
                .findFirst();
    }

    private String normalizeLine(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String mergeWarning(String base, String addition) {
        if (base == null || base.isBlank()) {
            return addition;
        }
        return base + "; " + addition;
    }

    private record KeyLine(String online, boolean offline) {}
    private record CommentPayload(String value, String mode) {}
    private record WhiteAdminOrderDetails(String comment, String legalEntityInfoJson) {}

    public record LegacySyncOutcome(
            boolean waCommentSaved,
            boolean localCommentSaved,
            boolean moveProcessedApplied,
            String warning
    ) {
        static LegacySyncOutcome warning(String warning) {
            return new LegacySyncOutcome(false, false, false, warning);
        }
    }
}
