package com.zillya.timonfech.zillwrapper.core.communication;

import org.springframework.stereotype.Service;

@Service
public class TelegramTextRenderer {

    public String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public String bold(String value) {
        return "<b>" + escapeHtml(value) + "</b>";
    }

    public String code(String value) {
        return "<code>" + escapeHtml(value) + "</code>";
    }

    public String link(String label, String url) {
        return "<a href=\"" + escapeHtml(url) + "\">" + escapeHtml(label) + "</a>";
    }

    public String renderHtml(String raw) {
        return raw == null ? "" : raw;
    }

    public String renderPlainFallback(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
    }
}

