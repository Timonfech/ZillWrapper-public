package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.whiteadmin.WhiteAdminOrdersParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhiteAdminPlaceholderOrderService {

    private final AbstractWhiteAdminClient client;
    private final WhiteAdminOrdersParser whiteAdminOrdersParser;

    @Value("${whiteadmin.orders.createUrl}")
    private String createUrl;

    public Long createAndResolveLast3(String firmName,
                                      String fio,
                                      String phone,
                                      String email,
                                      String docAddr,
                                      String comment) {
        long latestBefore = safeFetchLatestOrderId();
        log.info("WA placeholder create start: latestBefore={} firmPresent={} fioPresent={} phonePresent={} emailPresent={} docAddrPresent={} commentPresent={}",
                latestBefore,
                !safe(firmName).isBlank(),
                !safe(fio).isBlank(),
                !safe(phone).isBlank(),
                !safe(email).isBlank(),
                docAddr != null && !docAddr.isBlank(),
                comment != null && !comment.isBlank());
        if (safe(email).isBlank()) {
            throw new IllegalStateException("WA placeholder create requires email");
        }
        if (safe(fio).isBlank()) {
            throw new IllegalStateException("WA placeholder create requires fio (full name)");
        }
        if (safe(phone).isBlank()) {
            throw new IllegalStateException("WA placeholder create requires phone");
        }
        sendCreateRequest(firmName, fio, phone, email, docAddr, comment);
        long latestAfter = safeFetchLatestOrderId();
        log.info("WA placeholder create probe: latestBefore={} latestAfter={}", latestBefore, latestAfter);
        if (latestAfter <= latestBefore) {
            throw new IllegalStateException("WA placeholder order was not created (latest id did not increase)");
        }
        List<Long> lastThree = whiteAdminOrdersParser.fetchLastOrderIds(3);
        log.info("WA placeholder create top3 ids after create: {}", lastThree);
        for (Long orderId : lastThree) {
            if (orderId == null || orderId <= latestBefore) {
                log.debug("WA placeholder candidate skipped id={} reason=not-new", orderId);
                continue;
            }
            try {
                WhiteAdminOrdersParser.OrderMatchCard card = whiteAdminOrdersParser.loadOrderMatchCard(orderId);
                if (matches(card, firmName, fio, phone, email, docAddr, comment)) {
                    log.info("WA placeholder matched new order id={}", orderId);
                    return orderId;
                }
                log.info("WA placeholder candidate mismatch id={}", orderId);
            } catch (Exception ex) {
                log.debug("WA placeholder match skip for id={} reason={}", orderId, ex.getMessage());
            }
        }
        throw new IllegalStateException("WA order was created but not matched among new last 3 orders");
    }

    private void sendCreateRequest(String firmName,
                                   String fio,
                                   String phone,
                                   String email,
                                   String docAddr,
                                   String comment) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("count", "5"));
        params.add(new BasicNameValuePair("term", "12"));
        params.add(new BasicNameValuePair("fixed_price", ""));
        params.add(new BasicNameValuePair("firm_name", normalizeForPost(firmName)));
        params.add(new BasicNameValuePair("fio", normalizeForPost(fio)));
        params.add(new BasicNameValuePair("phone", normalizeForPost(phone)));
        params.add(new BasicNameValuePair("email", normalizeForPost(email)));
        if (docAddr != null && !docAddr.isBlank()) {
            params.add(new BasicNameValuePair("doc_addr", docAddr));
        }
        if (comment != null && !comment.isBlank()) {
            params.add(new BasicNameValuePair("comment", comment));
        }
        params.add(new BasicNameValuePair("purchase", ""));
        params.add(new BasicNameValuePair("choosen_payment", "pdf_u"));
        try {
            String response = client.executePostForm(createUrl, params, false);
            if (response != null && !response.isBlank()) {
                String compact = response.replaceAll("\\s+", " ").trim();
                String snippet = compact.length() > 220 ? compact.substring(0, 220) + "..." : compact;
                log.info("WA placeholder create response snippet={}", snippet);
            }
        } catch (IOException e) {
            throw new IllegalStateException("WA placeholder order create failed: " + e.getMessage(), e);
        }
    }

    private boolean matches(WhiteAdminOrdersParser.OrderMatchCard card,
                            String firmName,
                            String fio,
                            String phone,
                            String email,
                            String docAddr,
                            String comment) {
        boolean nameMatches = eq(card.clientName(), firmName) || eq(card.clientName(), fio);
        return nameMatches
                && eq(card.email(), email)
                && eq(card.phone(), phone)
                && eq(card.clientComment(), comment)
                && eq(card.docAddress(), docAddr);
    }

    private boolean eq(String a, String b) {
        String aa = normalize(a);
        String bb = normalize(b);
        if (bb == null) {
            return true;
        }
        return Objects.equals(aa, bb);
    }

    private String normalize(String v) {
        if (v == null) {
            return null;
        }
        String trimmed = v.trim().replace('\u00A0', ' ');
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private long safeFetchLatestOrderId() {
        try {
            return whiteAdminOrdersParser.fetchLatestId();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot resolve latest WA order id: " + ex.getMessage(), ex);
        }
    }

    private String safe(String v) {
        if (v == null) {
            return "";
        }
        return v.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForPost(String v) {
        String s = safe(v);
        return s.isBlank() ? "" : s;
    }

}
