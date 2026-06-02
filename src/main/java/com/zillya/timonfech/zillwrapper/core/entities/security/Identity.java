package com.zillya.timonfech.zillwrapper.core.entities.security;

import com.zillya.timonfech.zillwrapper.core.source.SourceType;

import java.util.Map;

/**
 * Common representation of a request's identity.
 * Unifies various transport-specific identifiers into a portable format.
 */
public interface Identity {
    /**
     * The unique database ID of the SourceEntity (e.g., a specific Telegram bot).
     */
    Long sourceId();
    /**
     * The origin of this identity (Enum).
     */
    SourceType sourceType();

    /**
     * All identifying factors available from this specific request (multi-factor support).
     */
    Map<UserSourceEntity.SecurityFactor, String> factors();
}