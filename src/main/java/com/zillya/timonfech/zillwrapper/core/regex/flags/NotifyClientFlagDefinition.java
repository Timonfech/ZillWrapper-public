package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class NotifyClientFlagDefinition extends SimpleFlagDefinition {

    public static final String KEY = "notify_client";

    public NotifyClientFlagDefinition() {
        super(KEY, List.of("c", "client"));
    }
}

