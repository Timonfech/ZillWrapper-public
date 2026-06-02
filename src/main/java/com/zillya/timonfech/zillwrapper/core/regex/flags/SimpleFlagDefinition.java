package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class SimpleFlagDefinition implements FlagDefinition {

    private final String key;
    private final List<String> aliases;

    public SimpleFlagDefinition(String key, List<String> aliases) {
        this.key = key;
        this.aliases = List.copyOf(aliases);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public boolean matches(String alias) {
        return aliases.stream().anyMatch(value -> value.equalsIgnoreCase(alias));
    }

    @Override
    public Boolean valueFor(String alias) {
        return Boolean.TRUE;
    }
}
