package com.zillya.timonfech.zillwrapper.api.masking;

public enum MaskingFieldType {
    ONLINE_KEY("ONLINE_KEY_PLACEHOLDER"),
    OFFLINE_KEY("OFFLINE_KEY_PLACEHOLDER"),
    NAME("NAME_PLACEHOLDER"),
    EMAIL("EMAIL_PLACEHOLDER"),
    PHONE("PHONE_PLACEHOLDER");

    private final String prefix;

    MaskingFieldType(String prefix) {
        this.prefix = prefix;
    }

    public String placeholder(int index) {
        return prefix + "{" + index + "}";
    }
}
