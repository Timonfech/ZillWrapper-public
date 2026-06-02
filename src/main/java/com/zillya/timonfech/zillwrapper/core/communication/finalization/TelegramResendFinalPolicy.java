package com.zillya.timonfech.zillwrapper.core.communication.finalization;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class TelegramResendFinalPolicy implements FinalNotificationPolicy {

    private final AbsSender telegramSender;

    @Override
    public String kind() {
        return "RESEND_SUCCESS";
    }

    @Override
    public boolean supports(FinalNotificationContext context) {
        return context.parentOperation() != null
                && context.parentOperation().getOperationType() == OperationType.RESEND_NOTIFICATION;
    }

    @Override
    public void notify(FinalNotificationContext context) {
        Long chatId = context.binding().getChatId();
        if (chatId == null) {
            log.warn("Resend final notify skipped: chatId is null");
            return;
        }

        String text = context.orderId() == null
                ? "Resend completed successfully."
                : "Resend completed successfully for order #" + context.orderId() + ".";
        StringBuilder sb = new StringBuilder(text);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (context.stageWarnings() != null) {
            warnings.addAll(context.stageWarnings());
        }
        if (context.nonCriticalWarnings() != null) {
            warnings.addAll(context.nonCriticalWarnings());
        }
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:");
            for (String warning : warnings) {
                sb.append("\n- ").append(warning);
            }
        }

        Integer controlMessageId = context.binding().getControlMessageId();
        if (controlMessageId == null) {
            log.warn("Resend final notify skipped: control message id is null for chatId={}", chatId);
            return;
        }
        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(controlMessageId)
                    .text(sb.toString())
                    .build());
            try {
                telegramSender.execute(EditMessageReplyMarkup.builder()
                        .chatId(chatId.toString())
                        .messageId(controlMessageId)
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException e) {
                if (e.getMessage() == null || !e.getMessage().contains("message is not modified")) {
                    throw e;
                }
                log.debug("Resend final keyboard already cleared chat={} message={}", chatId, controlMessageId);
            }
            log.info("Resend final control message edited chatId={} messageId={}", chatId, controlMessageId);
        } catch (TelegramApiException e) {
            log.warn("Failed to edit resend final telegram message chat={} message={}: {}",
                    chatId,
                    controlMessageId,
                    e.getMessage());
        }
    }
}
