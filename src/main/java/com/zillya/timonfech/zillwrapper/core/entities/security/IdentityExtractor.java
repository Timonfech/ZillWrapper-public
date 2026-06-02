package com.zillya.timonfech.zillwrapper.core.entities.security;


import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;

/**
 * Service to extract specialized identity from a source event
 */
public interface IdentityExtractor<T extends InboundEvent<?>> {
    /**
     * Extracts an identity from the given transport-specific event
     */
    Identity extract(T event);

    /**
     * @return the source type supported by this extractor.
     */
    SourceType sourceType();
}

