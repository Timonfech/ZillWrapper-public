package com.zillya.timonfech.zillwrapper.core.subscription.notifications;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.repos.OperationExecutionRepository;
import com.zillya.timonfech.zillwrapper.core.repos.SourceRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.subscription.events.SubscriptionDetailedDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSubscriptionNotificationHandler implements SubscriptionSourceNotificationHandler {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SourceRepository sourceRepository;
    private final OperationExecutionRepository operationExecutionRepository;
    private final TelegramOperationBindingRepository bindingRepository;
    private final AbsSender telegramSender;

    @Override
    public boolean supports(Long sourceId) {
        if (sourceId == null) {
            return false;
        }
        SourceEntity source = sourceRepository.findById(sourceId).orElse(null);
        return source != null && source.getType() == SourceType.TELEGRAM;
    }

    @Override
    public void notifyDetailedDelta(SubscriptionDetailedDeltaEvent event) {
        if (event.getOrderId() == null) {
            return;
        }
        Optional<OperationExecutionEntity> orderCreationStage = operationExecutionRepository
                .findByEntityIdAndOperationTypeOrderByCreatedAtDesc(event.getOrderId(), OperationType.ORDER_CREATION)
                .stream()
                .findFirst();
        if (orderCreationStage.isEmpty() || orderCreationStage.get().getParentId() == null) {
            return;
        }
        BigInteger parentId = orderCreationStage.get().getParentId();
        bindingRepository.findByOperationId(parentId).ifPresent(binding -> {
            if (binding.getChatId() == null) {
                return;
            }
            String text = buildDetailedText(event);
            try {
                SendMessage.SendMessageBuilder builder = SendMessage.builder()
                        .chatId(binding.getChatId().toString())
                        .text(text);
                if (binding.getControlMessageId() != null) {
                    builder.replyToMessageId(binding.getControlMessageId()).allowSendingWithoutReply(true);
                }
                telegramSender.execute(builder.build());
            } catch (TelegramApiException ex) {
                log.warn("Failed to send subscription detailed notify chatId={} orderId={}: {}",
                        binding.getChatId(),
                        event.getOrderId(),
                        ex.getMessage());
            }
        });
    }

    private String buildDetailedText(SubscriptionDetailedDeltaEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Detailed subscription update").append("\n");
        sb.append("Order #").append(event.getOrderId() == null ? "-" : event.getOrderId()).append("\n");
        sb.append("License: ").append(event.getKeyPrefix() == null ? "-" : event.getKeyPrefix()).append("\n");
        if (event.getChangedAt() != null) {
            sb.append("Changed at: ")
                    .append(DTF.format(event.getChangedAt().atZone(ZoneId.systemDefault())))
                    .append("\n");
        }
        if (event.getDeltas() == null || event.getDeltas().isEmpty()) {
            sb.append("No field-level changes.");
            return sb.toString();
        }
        for (SubscriptionDetailedDeltaEvent.FieldDelta delta : event.getDeltas()) {
            sb.append(delta.field())
                    .append(": ")
                    .append(delta.before() == null || delta.before().isBlank() ? "-" : delta.before())
                    .append(" -> ")
                    .append(delta.after() == null || delta.after().isBlank() ? "-" : delta.after())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
