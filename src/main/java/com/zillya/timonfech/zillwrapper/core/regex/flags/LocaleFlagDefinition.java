package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;
import java.util.regex.Pattern;

public class LocaleFlagDefinition implements ParameterFlagDefinition {

    public static final String KEY = "locale";
    private static final Pattern LOCALE_PATTERN = Pattern.compile("(?i)[a-z]{2,8}(?:-[a-z0-9]{2,8})*");

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> aliases() {
        return List.of("l", "locale");
    }

    @Override
    public Pattern valuePattern() {
        return LOCALE_PATTERN;
    }
}
