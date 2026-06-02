package com.zillya.timonfech.zillwrapper.core.links;

public record ExternalLink(
        Kind kind,
        Source source,
        String label,
        String url
) {
    public enum Kind {
        LICENSE,
        ORDER
    }

    public enum Source {
        DINO,
        WHITE_ADMIN_ZAB,
        WHITE_ADMIN_ZIS2,
        WHITE_ADMIN_ORDER
    }
}

