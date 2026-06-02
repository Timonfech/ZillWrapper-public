package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class TextFlagDefinition extends SimpleFlagDefinition {

    public static final String KEY = "text";

    public TextFlagDefinition() {
        super(KEY, List.of("t", "text"));
    }
}
