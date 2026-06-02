package com.zillya.timonfech.zillwrapper.core.communication.sections;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.communication.KeyPreviewFormatter;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeliveredKeysSectionRenderer implements ControlMessageSectionRenderer {

    private final LicenseRepository licenseRepository;
    private final OrderItemRepository orderItemRepository;
    private final KeyPreviewFormatter keyPreviewFormatter;

    @Override
    public boolean supports(ControlMessageContext context) {
        OperationStatus status = context.rootExecution() == null ? null : context.rootExecution().getStatus();
        return status == OperationStatus.DONE
                || status == OperationStatus.PARTIALLY_DONE
                || status == OperationStatus.FAILED;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String render(ControlMessageContext context) {
        Long orderId = resolveOrderId(context);
        if (orderId == null) {
            return "";
        }
        String keysBlock = keyPreviewFormatter.renderFinalRequested(
                licenseRepository.findByOrderId(orderId),
                orderItemRepository.findByOrderId(orderId)
        );
        if (keysBlock == null || keysBlock.isBlank() || "Keys: not available.".equals(keysBlock)) {
            return "";
        }
        return keysBlock;
    }

    private Long resolveOrderId(ControlMessageContext context) {
        if (context.rootExecution() == null) {
            return null;
        }
        Optional<OperationExecutionEntity> orderCreationChild = context.children().stream()
                .filter(child -> child.getOperationType() == OperationType.ORDER_CREATION)
                .filter(child -> child.getStatus() == OperationStatus.DONE)
                .filter(child -> child.getEntityId() != null)
                .findFirst();
        return orderCreationChild.map(OperationExecutionEntity::getEntityId)
                .orElse(context.rootExecution().getEntityId());
    }
}

