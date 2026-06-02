package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class SubscribeFlagDefinition extends PositiveNegativeFlagDefinition {

    public static final String KEY = "subscribe";

    public SubscribeFlagDefinition() {
        super(KEY, List.of("s", "subscribe"), List.of("uns", "unsubscribe"));
    }
}
