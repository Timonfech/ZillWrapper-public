package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.services.OperationExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OperationAuthorizationService {

    private final OperationExecutionService operationExecutionService;
    private final Set<OperationType> managerAllowedOperations;

    public OperationAuthorizationService(
            OperationExecutionService operationExecutionService,
            @Value("${security.manager.allowed-operations:MODIFY_STATUS,DETACH_ACTIVATIONS,RESEND_NOTIFICATION,LICENSE_SEARCH}") String managerAllowedOperationsRaw
    ) {
        this.operationExecutionService = operationExecutionService;
        this.managerAllowedOperations = parseOperationSet(managerAllowedOperationsRaw);
    }

    public boolean canControlOperation(UserEntity actor, BigInteger operationId, String action) {
        if (actor == null || actor.getRole() == null || operationId == null) {
            return false;
        }
        if (actor.getRole() == UserEntity.Role.ADMIN) {
            return true;
        }
        Optional<OperationExecutionEntity> rootOpt = operationExecutionService.getRootOperation(operationId);
        if (rootOpt.isEmpty()) {
            return false;
        }
        OperationExecutionEntity root = rootOpt.get();
        return root.getInitiatorUserId() != null && root.getInitiatorUserId().equals(actor.getId());
    }

    public boolean canStartOperation(UserEntity actor, OperationType operationType) {
        if (actor == null || actor.getRole() == null || operationType == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case MANAGER -> managerAllowedOperations.contains(operationType);
            case LLM_READONLY, USER, NONE -> false;
        };
    }

    private Set<OperationType> parseOperationSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(OperationType.class);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return OperationType.valueOf(s.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(v -> v != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OperationType.class)));
    }
}
