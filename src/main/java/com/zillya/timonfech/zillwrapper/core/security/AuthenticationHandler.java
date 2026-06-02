package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;

/**
 * Strategy for authenticating specific types of inbound events.
 * @param <T> The specialized InboundEvent type.
 */
public interface AuthenticationHandler<T extends InboundEvent<?>> {
    
    /**
     * Authenticates the event and returns the associated user.
     * Throws AuthenticationException if fails.
     */
    UserEntity authenticate(T event);

    /**
     * Returns true if this handler can process the given event.
     */
    boolean supports(InboundEvent<?> event);
}
