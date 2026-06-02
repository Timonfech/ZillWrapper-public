package com.zillya.timonfech.zillwrapper.core.pipeline;

public record LegacySyncDecision(
        boolean appendComment,
        boolean moveToProcessed,
        String reason
) {
}
