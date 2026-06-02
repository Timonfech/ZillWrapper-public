package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;
import java.util.regex.Pattern;

public interface ParameterFlagDefinition {

    String key();

    List<String> aliases();

    Pattern valuePattern();

    default boolean matchesAlias(String alias) {
        return aliases().stream().anyMatch(a -> a.equalsIgnoreCase(alias));
    }
}
