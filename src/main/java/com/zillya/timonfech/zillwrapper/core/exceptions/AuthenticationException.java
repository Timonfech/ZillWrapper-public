package com.zillya.timonfech.zillwrapper.core.exceptions;

import com.zillya.timonfech.zillwrapper.core.entities.security.AuthErrorReason;
import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
@Getter
public class AuthenticationException extends RuntimeException {

    private final AuthErrorReason reason;
    private final Long sourceId;
    private final Map<UserSourceEntity.SecurityFactor, String> providedFactors;

    public AuthenticationException(AuthErrorReason reason, Long sourceId, String message) {
        this(reason, sourceId, Map.of(), message);
    }

    public AuthenticationException(
            AuthErrorReason reason,
            Long sourceId,
            Map<UserSourceEntity.SecurityFactor, String> providedFactors,
            String message) {
        super(message);
        this.reason = reason;
        this.sourceId = sourceId;
        this.providedFactors = providedFactors;
    }
}