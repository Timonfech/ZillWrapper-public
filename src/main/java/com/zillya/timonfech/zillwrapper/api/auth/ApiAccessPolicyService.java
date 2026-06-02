package com.zillya.timonfech.zillwrapper.api.auth;

import com.zillya.timonfech.zillwrapper.api.ApiException;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApiAccessPolicyService {

    @Value("${api.masking.allow-human:false}")
    private boolean maskingAllowHuman;

    public void requireReadAccess(ApiPrincipal principal) {
        if (principal == null || principal.role() == null) {
            throw ApiException.unauthorized("Unauthorized");
        }
        if (principal.role() == UserEntity.Role.ADMIN
                || principal.role() == UserEntity.Role.MANAGER
                || principal.role() == UserEntity.Role.LLM_READONLY) {
            return;
        }
        throw ApiException.forbidden("Read access denied");
    }

    public void requireMaskingSessionAccess(ApiPrincipal principal) {
        if (principal == null || principal.role() == null) {
            throw ApiException.unauthorized("Unauthorized");
        }
        if (principal.role() == UserEntity.Role.LLM_READONLY) {
            return;
        }
        if (maskingAllowHuman && (principal.role() == UserEntity.Role.ADMIN || principal.role() == UserEntity.Role.MANAGER)) {
            return;
        }
        throw ApiException.forbidden("Masking session is not allowed for this principal");
    }

    public void requireMaskingEnabledForRequest(ApiPrincipal principal, boolean maskingEnabled) {
        if (!maskingEnabled) {
            return;
        }
        requireMaskingSessionAccess(principal);
    }
}
