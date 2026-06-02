package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.List;

public interface FlagDefinition {

    String key();

    List<String> aliases();

    boolean matches(String alias);

    Boolean valueFor(String alias);
}
