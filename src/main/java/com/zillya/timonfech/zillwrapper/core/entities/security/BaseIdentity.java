package com.zillya.timonfech.zillwrapper.core.entities.security;

import com.zillya.timonfech.zillwrapper.core.source.SourceType;

import java.util.Map;

/**
 * Basic immutable implementation of Identity.
 */
public record BaseIdentity(
        Long sourceId,
        SourceType sourceType, // ID from SourceEntity
        Map<UserSourceEntity.SecurityFactor, String> factors) implements Identity {
    public BaseIdentity {
        factors = Map.copyOf(factors);
    }

    @Override
    public SourceType sourceType() {
        return this.sourceType;
    }
}
