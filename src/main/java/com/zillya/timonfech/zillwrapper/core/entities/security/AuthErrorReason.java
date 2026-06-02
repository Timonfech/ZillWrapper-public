package com.zillya.timonfech.zillwrapper.core.entities.security;

/**
 * Categorizes the specific reason for an authentication failure.
 */
public enum AuthErrorReason {
    /** The specified source ID does not exist in the system. */
    SOURCE_NOT_FOUND,

    /** No user found matching the provided identity factors for this source. */
    USER_NOT_FOUND,

    /** The user was found, but their account is currently disabled. */
    USER_INACTIVE,

    /** The authentication source (token/access) has passed its expiration date. */
    SOURCE_EXPIRED,

    /** One or more required factors are missing from the request. */
    MISSING_FACTORS
}