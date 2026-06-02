package com.zillya.timonfech.zillwrapper.core.regex.order;

import java.util.List;

public record ParsedOrderRequest(
        ParsedOrderReference reference,
        String email,
        List<String> emails,
        List<ParsedOrderItem> items,
        String localeTag,
        Boolean partner
) {
    public ParsedOrderRequest {
        emails = emails == null ? List.of() : emails.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if ((email == null || email.isBlank()) && !emails.isEmpty()) {
            email = emails.getFirst();
        }
    }
}
