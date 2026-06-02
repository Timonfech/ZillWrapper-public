package com.zillya.timonfech.zillwrapper.core.exceptions;

import lombok.Getter;

@Getter
public class RoutingException extends RuntimeException {
    private final boolean recoverable;

    public RoutingException(String message) {
        this(message, true);
    }

    public RoutingException(String message, boolean recoverable) {
        super(message);
        this.recoverable = recoverable;
    }
}
