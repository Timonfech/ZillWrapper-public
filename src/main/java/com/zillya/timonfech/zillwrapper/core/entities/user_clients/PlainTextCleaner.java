package com.zillya.timonfech.zillwrapper.core.entities.user_clients;

class PlainTextCleaner {
    public static void zero(String s) {

    }
    public static void zero(char[] c) { if (c!=null) java.util.Arrays.fill(c, '\0'); }
    public static void zero(byte[] b) { if (b!=null) java.util.Arrays.fill(b, (byte)0); }
}
