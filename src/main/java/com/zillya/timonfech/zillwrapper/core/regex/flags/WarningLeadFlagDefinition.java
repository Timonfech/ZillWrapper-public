package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;
import java.util.regex.Pattern;

public class WarningLeadFlagDefinition implements ParameterFlagDefinition {

    public static final String KEY = "warning_lead";
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "^\\d{1,5}\\s*(?:m|min|h|hr|d|day|mo|mon|month)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> aliases() {
        return List.of("w", "warn");
    }

    @Override
    public Pattern valuePattern() {
        return VALUE_PATTERN;
    }
}

