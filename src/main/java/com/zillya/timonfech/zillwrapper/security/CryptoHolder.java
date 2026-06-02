package com.zillya.timonfech.zillwrapper.security;

// static holder for use in JPA AttributeConverter
public class CryptoHolder {
    private static CryptoUtils instance;
    public static void set(CryptoUtils c) { instance = c; }
    public static CryptoUtils get() { return instance; }
}