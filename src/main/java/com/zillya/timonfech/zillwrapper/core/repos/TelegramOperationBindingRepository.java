package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;

@Repository
public interface TelegramOperationBindingRepository extends JpaRepository<TelegramOperationBindingEntity, Long> {
    java.util.Optional<TelegramOperationBindingEntity> findByOperationId(BigInteger operationId);
    List<TelegramOperationBindingEntity> findByChatId(Long chatId);
    java.util.Optional<TelegramOperationBindingEntity> findByChatIdAndControlMessageId(Long chatId, Integer controlMessageId);
    List<TelegramOperationBindingEntity> findByControlMessageId(Integer controlMessageId);
    java.util.Optional<TelegramOperationBindingEntity> findByActivePreviewId(String activePreviewId);
    java.util.Optional<TelegramOperationBindingEntity> findByChatIdAndActivePreviewId(Long chatId, String activePreviewId);
    java.util.Optional<TelegramOperationBindingEntity> findByChatIdAndSourceMessageId(Long chatId, Integer sourceMessageId);
    List<TelegramOperationBindingEntity> findAllByChatIdAndSourceMessageIdOrderByPreviewCreatedAtDesc(Long chatId, Integer sourceMessageId);
}
