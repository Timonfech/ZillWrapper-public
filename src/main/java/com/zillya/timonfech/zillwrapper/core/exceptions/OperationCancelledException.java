package com.zillya.timonfech.zillwrapper.core.exceptions;

public class OperationCancelledException extends RuntimeException {
    public OperationCancelledException(String message) {
        super(message);
    }
}
