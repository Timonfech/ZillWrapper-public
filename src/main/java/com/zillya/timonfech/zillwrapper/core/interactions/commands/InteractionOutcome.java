package com.zillya.timonfech.zillwrapper.core.interactions.commands;

import com.zillya.timonfech.zillwrapper.core.search.SearchSession;
import lombok.Builder;

@Builder
public record InteractionOutcome(
        OutcomeType type,
        String message,
        SearchSession session,
        boolean actionMode,
        String actionLabel
) {
    public enum OutcomeType {
        VIEW,
        STARTED,
        EXPIRED,
        ERROR,
        NO_MATCH
    }
}

