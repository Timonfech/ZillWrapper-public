package com.zillya.timonfech.zillwrapper.apis;

public final class KeyMarkersUtils {
    public static final String BEGIN = "--------- KEY BEGIN ---------";
    public static final String END   = "--------- KEY END ---------";

    private KeyMarkersUtils() { /* utility */ }

    public static boolean hasBegin(String s) {
        return s != null && trimStart(s).startsWith(BEGIN);
    }

    public static boolean hasEnd(String s) {
        return s != null && trimEnd(s).endsWith(END);
    }

    public static String removeMarkers(String s) {
        if (s == null) return null;
        String t = trimBoth(s);
        if (t.startsWith(BEGIN)) t = t.substring(BEGIN.length());
        t = trimBoth(t);
        if (t.endsWith(END)) t = t.substring(0, t.length() - END.length());
        return trimBoth(t);
    }

    public static String addMarkers(String s) {
        if (s == null) return null;
        String body = trimBoth(s);
        return BEGIN + System.lineSeparator() + body + System.lineSeparator() + END;
    }

    public static String toggleMarkers(String s) {
        if (s == null) return null;
        return (hasBegin(s) || hasEnd(s)) ? removeMarkers(s) : addMarkers(s);
    }

    private static String trimStart(String s) {
        int i = 0, n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        return (i == 0) ? s : s.substring(i);
    }

    private static String trimEnd(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return (i == s.length() - 1) ? s : s.substring(0, i + 1);
    }

    private static String trimBoth(String s) {
        return trimEnd(trimStart(s));
    }
}
