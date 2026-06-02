package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.Map;

public record FlagParseResult(Map<String, Boolean> values) {

    public Boolean get(String key) {
        return values.get(key);
    }
}
