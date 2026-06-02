package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OrderReferenceLineParser {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^.+$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ADDRESS_FLAG_PATTERN = Pattern.compile("^(?:ad?r?e?s?s?|addr(?:ess)?|ад?р?е?с?а?)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COMMENT_FLAG_PATTERN = Pattern.compile("^(?:co?m?m?e?n?t?|com(?:ment)?|ко?м?м?е?н?н?т?а?р?и?й?)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final OrderRepository orderRepository;

    @Value("${order.reference.white-admin-window:7000}")
    private long whiteAdminWindow;

    @Value("${order.reference.portal-window:700}")
    private long portalWindow;

    @Value("${order.reference.white-admin-baseline:96412}")
    private long whiteAdminBaseline;

    @Value("${order.reference.portal-baseline:2993}")
    private long portalBaseline;

    public boolean matches(String line) {
        return line != null && REFERENCE_PATTERN.matcher(line).matches();
    }

    public ParsedOrderReference parse(String line) throws OrderParseException {
        if (!matches(line)) {
            throw new OrderParseException("First line must be order id or reference comment");
        }
        String raw = line == null ? "" : line.trim();
        ParsedRefExtras extras = extractExtras(raw);
        String normalized = extras.identifier();
        if (normalized.chars().allMatch(Character::isDigit)) {
            long value = Long.parseLong(normalized);

            Long maxWhiteAdminId = orderRepository.findMaxWhiteAdminId();
            Long maxPortalId = orderRepository.findMaxPortalId();
            long whiteAdminAnchor = (maxWhiteAdminId != null && maxWhiteAdminId > 0) ? maxWhiteAdminId : whiteAdminBaseline;
            long portalAnchor = (maxPortalId != null && maxPortalId > 0) ? maxPortalId : portalBaseline;

            boolean looksLikeWhiteAdmin = inRange(value, whiteAdminAnchor, whiteAdminWindow);
            boolean looksLikePortal = inRange(value, portalAnchor, portalWindow);

            if (looksLikeWhiteAdmin && !looksLikePortal) {
                return new ParsedOrderReference(null, value, null, extras.docAddress(), extras.waComment());
            }
            if (looksLikePortal && !looksLikeWhiteAdmin) {
                return new ParsedOrderReference(value, null, null, extras.docAddress(), extras.waComment());
            }
            if (looksLikePortal && looksLikeWhiteAdmin) {
                long whiteDiff = distance(value, whiteAdminAnchor);
                long portalDiff = distance(value, portalAnchor);
                if (whiteDiff <= portalDiff) {
                    return new ParsedOrderReference(null, value, null, extras.docAddress(), extras.waComment());
                }
                return new ParsedOrderReference(value, null, null, extras.docAddress(), extras.waComment());
            }

            if (value < 3000) {
                return new ParsedOrderReference(value, null, null, extras.docAddress(), extras.waComment());
            }
            return new ParsedOrderReference(null, value, null, extras.docAddress(), extras.waComment());
        }
        return new ParsedOrderReference(null, null, normalized, extras.docAddress(), extras.waComment());
    }

    private ParsedRefExtras extractExtras(String raw) {
        String identifier = raw == null ? "" : raw.trim();
        String addr = null;
        String comment = null;

        List<String> tokens = tokenizePreserveQuotes(identifier);
        List<String> kept = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (isAddressFlagToken(token)) {
                ExtractedFlag extracted = readFlagValue(tokens, i);
                addr = blankToNull(extracted.value());
                i = extracted.lastConsumedIndex();
                continue;
            }

            if (isCommentFlagToken(token)) {
                ExtractedFlag extracted = readFlagValue(tokens, i);
                comment = blankToNull(extracted.value());
                i = extracted.lastConsumedIndex();
                continue;
            }

            kept.add(token);
        }

        return new ParsedRefExtras(String.join(" ", kept).trim(), blankToNull(addr), blankToNull(comment));
    }

    private List<String> tokenizePreserveQuotes(String input) {
        List<String> out = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                cur.append(c);
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) {
            out.add(cur.toString());
        }
        return out;
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    private boolean isAddressFlagToken(String token) {
        String key = normalizeFlagToken(token);
        return key != null && ADDRESS_FLAG_PATTERN.matcher(key).matches();
    }

    private boolean isCommentFlagToken(String token) {
        String key = normalizeFlagToken(token);
        return key != null && COMMENT_FLAG_PATTERN.matcher(key).matches();
    }

    private String normalizeFlagToken(String token) {
        if (token == null || token.isBlank() || token.charAt(0) != '-') {
            return null;
        }
        String body = token.substring(1);
        int eq = body.indexOf('=');
        return eq >= 0 ? body.substring(0, eq) : body;
    }

    private ExtractedFlag readFlagValue(List<String> tokens, int flagIndex) {
        String token = tokens.get(flagIndex);
        StringBuilder value = new StringBuilder();
        int lastConsumed = flagIndex;

        int eq = token.indexOf('=');
        if (eq >= 0 && eq < token.length() - 1) {
            value.append(token.substring(eq + 1));
        }

        for (int j = flagIndex + 1; j < tokens.size(); j++) {
            String next = tokens.get(j);
            if (isAddressFlagToken(next) || isCommentFlagToken(next)) {
                break;
            }
            if (!value.isEmpty()) {
                value.append(' ');
            }
            value.append(next);
            lastConsumed = j;
        }

        return new ExtractedFlag(stripQuotes(value.toString().trim()), lastConsumed);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record ParsedRefExtras(String identifier, String docAddress, String waComment) {
    }

    private record ExtractedFlag(String value, int lastConsumedIndex) {
    }

    private boolean inRange(long value, long baseline, long window) {
        if (baseline <= 0) {
            return false;
        }
        return Math.abs(value - baseline) <= window;
    }

    private long distance(long value, long baseline) {
        return Math.abs(value - baseline);
    }
}
