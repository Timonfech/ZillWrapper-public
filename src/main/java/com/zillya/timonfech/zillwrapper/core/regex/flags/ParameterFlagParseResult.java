package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.Map;

public record ParameterFlagParseResult(Map<String, String> values) {

    public String get(String key) {
        return values.get(key);
    }
}
