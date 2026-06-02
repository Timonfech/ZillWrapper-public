package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import org.springframework.context.MessageSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ControlMessageContext(
        BigInteger rootOperationId,
        OperationExecutionEntity viewExecution,
        OperationExecutionEntity rootExecution,
        List<OperationExecutionEntity> children,
        Locale locale,
        MessageSource messageSource
) {
    public enum RenderMode {
        COMPACT_SUCCESS,
        DIAGNOSTIC
    }

    public String msg(String code) {
        return messageSource.getMessage(code, null, code, locale);
    }

    public RenderMode mode() {
        if (rootExecution == null || rootExecution.getStatus() != OperationStatus.DONE) {
            return RenderMode.DIAGNOSTIC;
        }
        boolean hadDiagnosticSignal = children != null && children.stream().anyMatch(child ->
                child.getStatus() == OperationStatus.PARTIALLY_DONE
                        || child.getStatus() == OperationStatus.FAILED
                        || child.getStatus() == OperationStatus.WAITING_INTERACTION);
        return hadDiagnosticSignal ? RenderMode.DIAGNOSTIC : RenderMode.COMPACT_SUCCESS;
    }

    public Long resolvedOrderId() {
        if (rootExecution == null) {
            return null;
        }
        Optional<OperationExecutionEntity> orderCreationChild = children == null ? Optional.empty() : children.stream()
                .filter(child -> child.getOperationType() == OperationType.ORDER_CREATION)
                .filter(child -> child.getStatus() == OperationStatus.DONE)
                .filter(child -> child.getEntityId() != null)
                .findFirst();
        return orderCreationChild.map(OperationExecutionEntity::getEntityId)
                .orElse(rootExecution.getEntityId());
    }
}
