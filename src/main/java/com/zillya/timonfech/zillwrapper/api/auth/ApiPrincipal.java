package com.zillya.timonfech.zillwrapper.api.auth;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;

public record ApiPrincipal(
        Long userId,
        String username,
        Long sourceId,
        UserEntity.Role role
) {
    public boolean isLlmReadonly() {
        return role == UserEntity.Role.LLM_READONLY;
    }
}
