package com.zillya.timonfech.zillwrapper.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    public ApiException(ApiErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ApiErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ApiErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ApiErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ApiErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
