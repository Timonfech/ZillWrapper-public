package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class PartnerFlagDefinition extends PositiveNegativeFlagDefinition {
    public static final String KEY = "partner";

    public PartnerFlagDefinition() {
        super(KEY, List.of("p", "partner"), List.of("np", "npartner", "notpartner", "unpartner"));
    }
}

