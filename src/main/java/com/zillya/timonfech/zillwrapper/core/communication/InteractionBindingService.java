package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;

import java.math.BigInteger;
import java.util.Optional;

public interface InteractionBindingService {
    Optional<TelegramOperationBindingEntity> resolveActiveTask(String taskId, Long chatId, Integer messageId);
    Optional<TelegramOperationBindingEntity> findByActiveTaskId(String taskId);
    void bindOperationToTask(String taskId, BigInteger operationId);
}
