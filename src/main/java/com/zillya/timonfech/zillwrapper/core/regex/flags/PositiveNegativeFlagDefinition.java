package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.ArrayList;
import java.util.List;

public class PositiveNegativeFlagDefinition implements FlagDefinition {

    private final String key;
    private final List<String> positiveAliases;
    private final List<String> negativeAliases;

    public PositiveNegativeFlagDefinition(String key,
                                          List<String> positiveAliases,
                                          List<String> negativeAliases) {
        this.key = key;
        this.positiveAliases = List.copyOf(positiveAliases);
        this.negativeAliases = List.copyOf(negativeAliases);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public List<String> aliases() {
        List<String> all = new ArrayList<>(positiveAliases);
        all.addAll(negativeAliases);
        return all;
    }

    @Override
    public boolean matches(String alias) {
        return positiveAliases.stream().anyMatch(value -> value.equalsIgnoreCase(alias))
                || negativeAliases.stream().anyMatch(value -> value.equalsIgnoreCase(alias));
    }

    @Override
    public Boolean valueFor(String alias) {
        if (negativeAliases.stream().anyMatch(value -> value.equalsIgnoreCase(alias))) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
