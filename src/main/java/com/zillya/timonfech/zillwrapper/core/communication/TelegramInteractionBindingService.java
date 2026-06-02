package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TelegramInteractionBindingService implements InteractionBindingService {

    private final TelegramOperationBindingRepository bindingRepository;

    @Override
    public Optional<TelegramOperationBindingEntity> resolveActiveTask(String taskId, Long chatId, Integer messageId) {
        Optional<TelegramOperationBindingEntity> bindingOpt = bindingRepository.findByChatIdAndActivePreviewId(chatId, taskId);
        if (bindingOpt.isEmpty()) {
            return Optional.empty();
        }
        TelegramOperationBindingEntity binding = bindingOpt.get();
        if (binding.getPreviewMessageId() == null || !binding.getPreviewMessageId().equals(messageId)) {
            return Optional.empty();
        }
        if (!TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())) {
            return Optional.empty();
        }
        if (binding.getPreviewExpiresAt() != null && Instant.now().isAfter(binding.getPreviewExpiresAt())) {
            return Optional.empty();
        }
        return Optional.of(binding);
    }

    @Override
    public Optional<TelegramOperationBindingEntity> findByActiveTaskId(String taskId) {
        return bindingRepository.findByActivePreviewId(taskId);
    }

    @Override
    public void bindOperationToTask(String taskId, BigInteger operationId) {
        bindingRepository.findByActivePreviewId(taskId).ifPresent(binding -> {
            binding.setOperationId(operationId);
            if (binding.getControlMessageId() == null && binding.getPreviewMessageId() != null) {
                binding.setControlMessageId(binding.getPreviewMessageId());
            }
            bindingRepository.save(binding);
        });
    }
}
