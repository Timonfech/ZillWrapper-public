package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;
import java.util.regex.Pattern;

public class SubscriptionIntervalFlagDefinition implements ParameterFlagDefinition {

    public static final String KEY = "subscription_interval_minutes";
    private static final Pattern VALUE_PATTERN = Pattern.compile("^\\d{1,6}$");

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> aliases() {
        return List.of("si", "sub-interval");
    }

    @Override
    public Pattern valuePattern() {
        return VALUE_PATTERN;
    }
}

