package com.zillya.timonfech.zillwrapper.core.subscription;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WarningLeadParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{1,5})\\s*(m|min|h|hr|d|day|mo|mon|month)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    public Optional<WarningLead> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int amount = Integer.parseInt(matcher.group(1));
        if (amount < 1) {
            return Optional.empty();
        }
        String unitRaw = matcher.group(2).toLowerCase(Locale.ROOT);
        SubscriptionLeadUnit unit = switch (unitRaw) {
            case "m", "min" -> SubscriptionLeadUnit.MINUTE;
            case "h", "hr" -> SubscriptionLeadUnit.HOUR;
            case "d", "day" -> SubscriptionLeadUnit.DAY;
            case "mo", "mon", "month" -> SubscriptionLeadUnit.MONTH;
            default -> null;
        };
        if (unit == null) {
            return Optional.empty();
        }
        return Optional.of(new WarningLead(amount, unit));
    }
}

