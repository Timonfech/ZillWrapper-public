package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.telegram.TelegramHelpFormatter;
import com.zillya.timonfech.zillwrapper.core.transport.telegram.commands.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramEnrichmentMenuOrchestrator {

    private final AbsSender telegramSender;
    private final MessageSource messageSource;
    private final TelegramHelpFormatter telegramHelpFormatter;
    private final List<TelegramCommand> availableCommands;

    public boolean canHandle(TelegramInboundEvent event) {
        Update update = event.getPayload();
        Long chatId = resolveChatId(update);
        Long userId = resolveUserId(update);
        if (chatId == null || userId == null) {
            return false;
        }
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            return data != null && data.startsWith("enrich_");
        }
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        String text = update.getMessage().getText().trim();
        return isHelp(text) || isEnrichment(text) || isStart(text);
    }

    public boolean tryHandle(TelegramInboundEvent event, UserEntity user) {
        Update update = event.getPayload();
        Locale locale = resolveLocale(update);
        Long chatId = resolveChatId(update);
        if (chatId == null) {
            return false;
        }

        if (update.hasCallbackQuery()) {
            return handleCallback(update.getCallbackQuery(), locale);
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }

        String text = update.getMessage().getText().trim();
        if (isHelp(text)) {
            sendMainKeyboard(chatId, locale);
            sendMessageHtml(chatId, telegramHelpFormatter.format(locale, availableCommands));
            return true;
        }
        if (isStart(text)) {
            sendMainKeyboard(chatId, locale);
            return true;
        }
        if (isEnrichment(text)) {
            sendMainKeyboard(chatId, locale);
            sendMessage(chatId, publicBuildEnrichmentDisabled(locale));
            return true;
        }
        return false;
    }

    private boolean handleCallback(CallbackQuery callback, Locale locale) {
        String data = callback.getData();
        if (data == null || !data.startsWith("enrich_")) {
            return false;
        }
        answerCallback(callback.getId());
        Long chatId = callback.getMessage() != null ? callback.getMessage().getChatId() : null;
        if (chatId != null) {
            sendMessage(chatId, publicBuildEnrichmentDisabled(locale));
        }
        return true;
    }

    private String publicBuildEnrichmentDisabled(Locale locale) {
        return messageSource.getMessage(
                "telegram.menu.enrichment.public_disabled",
                null,
                "Enrichment menu is disabled in the public build.",
                locale
        );
    }

    private void sendMainKeyboard(Long chatId, Locale locale) {
        KeyboardRow row = new KeyboardRow();
        row.add(KeyboardButton.builder().text(msg("telegram.menu.help.button", locale)).build());
        row.add(KeyboardButton.builder().text(msg("telegram.menu.enrichment.button", locale)).build());

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .resizeKeyboard(true)
                .keyboard(List.of(row))
                .build();
        sendMessage(chatId, msg("telegram.menu.welcome", locale), keyboard);
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessageHtml(Long chatId, String text) {
        for (String chunk : splitForTelegram(text, 3500)) {
            try {
                telegramSender.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(chunk)
                        .parseMode("HTML")
                        .build());
            } catch (TelegramApiException e) {
                log.warn("Failed to send Telegram HTML message chunk to {}: {}. Falling back to plain text.", chatId, e.getMessage());
                try {
                    telegramSender.execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(stripHtml(chunk))
                            .build());
                } catch (TelegramApiException fallbackEx) {
                    log.warn("Failed to send Telegram plain fallback message to {}: {}", chatId, fallbackEx.getMessage());
                }
            }
        }
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
    }

    private List<String> splitForTelegram(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        if (text.length() <= maxLen) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String candidate = current.isEmpty() ? paragraph : current + "\n\n" + paragraph;
            if (candidate.length() <= maxLen) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (paragraph.length() <= maxLen) {
                current.append(paragraph);
            } else {
                int start = 0;
                while (start < paragraph.length()) {
                    int end = Math.min(start + maxLen, paragraph.length());
                    parts.add(paragraph.substring(start, end));
                    start = end;
                }
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboardMarkup markup) {
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML");
            if (markup != null) {
                builder.replyMarkup(markup);
            }
            telegramSender.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send Telegram message to {}: {}", chatId, e.getMessage());
        }
    }

    private void answerCallback(String callbackId) {
        try {
            telegramSender.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.debug("Failed to answer callback {}: {}", callbackId, e.getMessage());
        }
    }

    private boolean isHelp(String text) {
        String normalized = normalizeCommandToken(text);
        return "/help".equalsIgnoreCase(normalized) || "help".equalsIgnoreCase(normalized);
    }

    private boolean isEnrichment(String text) {
        String normalized = normalizeCommandToken(text);
        return "/enrichment".equalsIgnoreCase(normalized) || "enrichment".equalsIgnoreCase(normalized);
    }

    private boolean isStart(String text) {
        String normalized = normalizeCommandToken(text);
        return "/start".equalsIgnoreCase(normalized) || "/menu".equalsIgnoreCase(normalized);
    }

    private String normalizeCommandToken(String text) {
        if (text == null) {
            return "";
        }
        String firstToken = text.trim().split("\\s+")[0];
        int mention = firstToken.indexOf('@');
        if (mention > 0) {
            return firstToken.substring(0, mention);
        }
        return firstToken;
    }

    private Locale resolveLocale(Update update) {
        String tag = null;
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            tag = update.getMessage().getFrom().getLanguageCode();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            tag = update.getCallbackQuery().getFrom().getLanguageCode();
        }
        if (tag == null || tag.isBlank()) {
            return Locale.forLanguageTag("uk");
        }
        String normalized = tag.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ru")) {
            normalized = "uk";
        }
        return Locale.forLanguageTag(normalized);
    }

    private Long resolveChatId(Update update) {
        if (update.hasMessage() && update.getMessage() != null) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    private Long resolveUserId(Update update) {
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            return update.getMessage().getFrom().getId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return null;
    }

    private String msg(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }
}
