package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class SubscribeDetailedFlagDefinition extends SimpleFlagDefinition {

    public static final String KEY = "subscribe_detailed";

    public SubscribeDetailedFlagDefinition() {
        super(KEY, List.of("sd", "subscribe-detailed"));
    }
}

