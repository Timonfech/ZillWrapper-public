package com.zillya.timonfech.zillwrapper.core.communication.finalization;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1000)
@RequiredArgsConstructor
public class TelegramDefaultFinalPolicy implements FinalNotificationPolicy {

    private final AbsSender telegramSender;

    @Override
    public String kind() {
        return "DEFAULT";
    }

    @Override
    public boolean supports(FinalNotificationContext context) {
        return true;
    }

    @Override
    public void notify(FinalNotificationContext context) {
        Long chatId = context.binding().getChatId();
        if (chatId == null) {
            return;
        }
        String text = buildText(context);

        Integer controlMessageId = context.binding().getControlMessageId();
        if (controlMessageId == null) {
            try {
                Message sent = telegramSender.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text)
                        .build());
                context.binding().setControlMessageId(sent.getMessageId());
            } catch (TelegramApiException e) {
                log.warn("Failed to send default final telegram message chat={}: {}", chatId, e.getMessage());
            }
            return;
        }

        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(controlMessageId)
                    .text(text)
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
                log.debug("Default final keyboard already cleared chat={} message={}", chatId, controlMessageId);
            }
        } catch (TelegramApiException e) {
            log.warn("Failed to edit default final telegram message chat={} message={}: {}",
                    chatId,
                    controlMessageId,
                    e.getMessage());
        }
    }

    private String buildText(FinalNotificationContext context) {
        List<String> warnings = new ArrayList<>();
        if (context.stageWarnings() != null) {
            warnings.addAll(context.stageWarnings());
        }
        if (context.nonCriticalWarnings() != null) {
            warnings.addAll(context.nonCriticalWarnings());
        }

        String statusResult = warnings.stream()
                .filter(w -> w != null && w.startsWith("STATUS_RESULT "))
                .findFirst()
                .orElse(null);

        StringBuilder sb = new StringBuilder(buildHeader(context, statusResult));

        boolean modifyStatusFlow = context.parentOperation() != null
                && context.parentOperation().getOperationType() == OperationType.MODIFY_STATUS;

        if (statusResult != null) {
            sb.append("\n").append(formatStatusResult(statusResult));
            warnings = warnings.stream()
                    .filter(w -> w == null || !w.startsWith("STATUS_RESULT "))
                    .toList();
        }

        if (modifyStatusFlow && statusResult != null) {
            return sb.toString();
        }

        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:");
            for (String warning : warnings) {
                sb.append("\n- ").append(warning);
            }
        }
        return sb.toString();
    }

    private String buildHeader(FinalNotificationContext context, String statusResult) {
        if (context.parentOperation() != null
                && context.parentOperation().getOperationType() == OperationType.MODIFY_STATUS
                && statusResult != null) {
            return "Status update completed.";
        }
        return "Operation completed successfully.";
    }

    private String formatStatusResult(String raw) {
        String updated = extractToken(raw, "detail_updated=");
        String failed = extractToken(raw, "detail_failed=");
        String unchanged = extractToken(raw, "detail_unchanged=");

        StringBuilder sb = new StringBuilder("License results:");
        appendDetails(sb, updated);
        appendDetails(sb, failed);
        appendDetails(sb, unchanged);
        return sb.toString();
    }

    private void appendDetails(StringBuilder sb, String csv) {
        if (csv == null || csv.isBlank() || "-".equals(csv)) {
            return;
        }
        String[] tokens = csv.contains("|") ? csv.split("\\|") : csv.split(",");
        for (String token : tokens) {
            String value = token == null ? "" : token.trim();
            if (!value.isBlank()) {
                sb.append("\n- ").append(value);
            }
        }
    }

    private String extractToken(String raw, String prefix) {
        int start = raw.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int valueStart = start + prefix.length();
        int end = raw.indexOf(" ", valueStart);
        if (end < 0) {
            end = raw.length();
        }
        return raw.substring(valueStart, end).trim();
    }
}
