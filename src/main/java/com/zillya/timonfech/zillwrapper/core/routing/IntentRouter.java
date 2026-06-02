package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;

/**
 * Abstraction for recognising user intent and building an operation context from a source event.
 *
 * Implementations parse the raw payload (text, callbacks, etc.) and return a typed
 * routing decision for pipeline, interaction/menu side-effects or ignore/error.
 *
 * @param <C> The specialized InboundEvent type this router understands.
 */
public interface IntentRouter<C extends InboundEvent<?>> {

    /**
     * Returns true if this router can process the given event.
     * Should be a fast, non-throwing check (e.g. regex match).
     */
    boolean canRoute(InboundEvent<?> event);

    /**
     * Parses the event into an {@link RoutingDecision} that describes the intended operation.
     * Called only when {@link #canRoute} returns true and the event is of type C.
     *
     * @throws com.zillya.timonfech.zillwrapper.core.exceptions.RoutingException if parsing fails.
     */
    RoutingDecision route(C event);
}
