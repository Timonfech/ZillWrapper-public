package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionKind;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OperationExecutionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReplyCorrelationResolver {
    private final TelegramOperationBindingRepository bindingRepository;
    private final OperationExecutionRepository operationExecutionRepository;

    public Optional<Long> resolveOrderIdByReplyCorrelation(Long chatId, Integer replyToMessageId) {
        if (chatId == null || replyToMessageId == null) {
            return Optional.empty();
        }
        Optional<TelegramOperationBindingEntity> binding = bindingRepository.findByChatIdAndControlMessageId(chatId, replyToMessageId);
        if (binding.isEmpty() || binding.get().getOperationId() == null) {
            return Optional.empty();
        }
        return resolveOrderIdFromOperation(binding.get().getOperationId());
    }

    public Optional<Long> resolveOrderIdFromOperation(BigInteger operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        Optional<OperationExecutionEntity> op = operationExecutionRepository.findById(operationId);
        if (op.isEmpty()) {
            return Optional.empty();
        }
        OperationExecutionEntity root = op.get().getExecutionKind() == OperationExecutionKind.PARENT
                ? op.get()
                : operationExecutionRepository.findById(op.get().getParentId()).orElse(null);
        if (root == null) {
            return Optional.empty();
        }
        List<OperationExecutionEntity> children = operationExecutionRepository.findByParentId(root.getId());
        return children.stream()
                .filter(c -> c.getOperationType() == OperationType.ORDER_CREATION && c.getEntityId() != null)
                .map(OperationExecutionEntity::getEntityId)
                .findFirst();
    }
}

