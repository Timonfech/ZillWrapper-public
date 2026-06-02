package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public class ExcelFlagDefinition extends PositiveNegativeFlagDefinition {

    public static final String KEY = "excel";

    public ExcelFlagDefinition() {
        super(KEY, List.of("e", "excel"), List.of("ne", "nexcel"));
    }
}
