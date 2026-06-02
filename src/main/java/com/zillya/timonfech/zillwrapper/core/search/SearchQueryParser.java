package com.zillya.timonfech.zillwrapper.core.search;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SearchQueryParser {

    public SearchQuery parse(String rawPayload) {
        SearchQuery.SearchQueryBuilder b = SearchQuery.builder().entityType(SearchEntityType.LICENSE);
        if (rawPayload == null || rawPayload.isBlank()) {
            return b.build();
        }
        String[] tokens = rawPayload.trim().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            if ("-p".equalsIgnoreCase(token) || "--product".equalsIgnoreCase(token)) {
                String value = consumeValue(tokens, i + 1);
                if (value != null && !value.isBlank()) {
                    b.productName(value);
                    i += consumedTokens(tokens, i + 1, value);
                }
                continue;
            }
            if (isSpaceSeparatedSelector(lower)) {
                String value = consumeValue(tokens, i + 1);
                if (value != null && !value.isBlank()) {
                    applySelector(b, lower, value);
                    i += consumedTokens(tokens, i + 1, value);
                }
                continue;
            }
            if (!token.contains("=")) {
                continue;
            }
            String[] p = token.split("=", 2);
            String k = p[0].trim().toLowerCase(Locale.ROOT);
            String v = p.length > 1 ? p[1].trim() : "";
            if (v.isBlank()) {
                continue;
            }
            applySelector(b, k, v);
        }
        return b.build();
    }

    private boolean isSpaceSeparatedSelector(String token) {
        return "entity".equals(token)
                || "e".equals(token)
                || "orderid".equals(token)
                || "oid".equals(token)
                || "woid".equals(token)
                || "wzid".equals(token)
                || "wid2".equals(token)
                || "pid".equals(token)
                || "lex".equals(token)
                || "kid".equals(token)
                || "p".equals(token)
                || "product".equals(token)
                || "kon".equals(token)
                || "kof".equals(token)
                || "comment".equals(token)
                || "cmt".equals(token);
    }

    private void applySelector(SearchQuery.SearchQueryBuilder b, String key, String value) {
        String v = stripQuotes(value);
        switch (key) {
            case "entity", "e" -> b.entityType("order".equalsIgnoreCase(v) ? SearchEntityType.ORDER : SearchEntityType.LICENSE);
            case "orderid", "oid" -> b.orderId(parseLong(v));
            case "woid" -> b.woid(parseLong(v));
            case "wzid" -> b.wzid(parseLong(v));
            case "wid2" -> b.wid2(parseLong(v));
            case "pid" -> b.pid(parseLong(v));
            case "lex" -> b.lex(parseLong(v));
            case "kid" -> b.kid(parseLong(v));
            case "p", "product" -> b.productName(v);
            case "kon" -> b.kon(v);
            case "kof" -> b.kof(v);
            case "comment", "cmt" -> b.comment(v);
            default -> {
            }
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String consumeValue(String[] tokens, int start) {
        if (start >= tokens.length) {
            return null;
        }
        String first = tokens[start];
        if (!first.startsWith("\"")) {
            return stripQuotes(first);
        }
        StringBuilder sb = new StringBuilder(first);
        int i = start + 1;
        while (i < tokens.length && !tokens[i - 1].endsWith("\"")) {
            sb.append(' ').append(tokens[i]);
            i++;
        }
        return stripQuotes(sb.toString());
    }

    private int consumedTokens(String[] tokens, int start, String parsedValue) {
        if (start >= tokens.length) {
            return 0;
        }
        if (!tokens[start].startsWith("\"")) {
            return 1;
        }
        int consumed = 1;
        while (start + consumed - 1 < tokens.length && !tokens[start + consumed - 1].endsWith("\"")) {
            consumed++;
            if (start + consumed > tokens.length) {
                break;
            }
        }
        return Math.max(consumed, 1);
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("\"")) {
            v = v.substring(1);
        }
        if (v.endsWith("\"")) {
            v = v.substring(0, v.length() - 1);
        }
        return v.trim();
    }
}
