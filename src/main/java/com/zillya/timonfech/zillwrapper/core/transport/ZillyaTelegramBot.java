package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Entry point for all Telegram updates.
 *
 * Responsibilities (Strictly):
 * 1. Receive raw {@link Update} from Telegram.
 * 2. Resolve the {@link SourceEntity}.
 * 3. Publish {@link TelegramInboundEvent} to the application context.
 */
@Slf4j
@Component
public class ZillyaTelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final SourceManagementService sourceService;
    private final ApplicationEventPublisher eventPublisher;

    public ZillyaTelegramBot(
            @Value("${telegram.bot.zill.bot-token}") String botToken,
            @Value("${telegram.bot.zill.bot-name}") String botUsername,
            SourceManagementService sourceService,
            ApplicationEventPublisher eventPublisher) {
        super(botToken);
        this.botUsername = botUsername;
        this.sourceService = sourceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @PostConstruct
    void logBotStarted() {
        log.info("Telegram bot context started. botName={}", botUsername);
    }

    @Override
    public void onUpdateReceived(Update update) {
        logIncomingUpdate(update);
        if (!update.hasMessage() && !update.hasCallbackQuery() && !update.hasEditedMessage()) {
            return;
        }

        try {
            SourceEntity source = resolveTelegramSource();

            // Step 2: Build Typed Inbound Event
            TelegramInboundEvent event = new TelegramInboundEvent(source, update);

            // Step 3: Offload to Orchestrator via Event System
            eventPublisher.publishEvent(event);
            Long publishedChatId = null;
            if (update.hasMessage() && update.getMessage() != null) {
                publishedChatId = update.getMessage().getChatId();
            } else if (update.hasEditedMessage() && update.getEditedMessage() != null) {
                publishedChatId = update.getEditedMessage().getChatId();
            } else if (update.hasCallbackQuery()
                    && update.getCallbackQuery() != null
                    && update.getCallbackQuery().getMessage() != null) {
                publishedChatId = update.getCallbackQuery().getMessage().getChatId();
            }
            log.trace("Published TelegramInboundEvent for chatId: {}", publishedChatId);

        } catch (Exception ex) {
            log.error("Failed to handle Telegram update: {}", ex.getMessage());
        }
    }

    private SourceEntity resolveTelegramSource() {
        return sourceService.getOrCreateSource(SourceType.TELEGRAM, botUsername);
    }

    public void sendErrorMessage(String chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("[ERROR] <b>Error:</b>\n\n<code>" + text + "</code>")
                    .parseMode("HTML")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    private void logIncomingUpdate(Update update) {
        if (update == null) {
            log.info("Telegram update received: null");
            return;
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery cb = update.getCallbackQuery();
            Long chatId = cb.getMessage() != null ? cb.getMessage().getChatId() : null;
            Integer messageId = cb.getMessage() != null ? cb.getMessage().getMessageId() : null;
            Long fromId = cb.getFrom() != null ? cb.getFrom().getId() : null;
            String username = cb.getFrom() != null ? cb.getFrom().getUserName() : null;
            log.info("Telegram callback update: fromId={}, username={}, chatId={}, messageId={}, callbackData={}",
                    fromId, username, chatId, messageId, cb.getData());
            return;
        }

        if (update.hasMessage()) {
            var m = update.getMessage();
            Long fromId = m.getFrom() != null ? m.getFrom().getId() : null;
            String username = m.getFrom() != null ? m.getFrom().getUserName() : null;
            Integer replyTo = m.getReplyToMessage() != null ? m.getReplyToMessage().getMessageId() : null;
            log.info("Telegram message update: fromId={}, username={}, chatId={}, messageId={}, replyToMessageId={}, text={}",
                    fromId, username, m.getChatId(), m.getMessageId(), replyTo, m.getText());
            return;
        }
        if (update.hasEditedMessage()) {
            var m = update.getEditedMessage();
            Long fromId = m.getFrom() != null ? m.getFrom().getId() : null;
            String username = m.getFrom() != null ? m.getFrom().getUserName() : null;
            log.info("Telegram edited message update: fromId={}, username={}, chatId={}, messageId={}, text={}",
                    fromId, username, m.getChatId(), m.getMessageId(), m.getText());
            return;
        }

        log.info("Telegram update received: unsupported payload (no message/callback), updateId={}", update.getUpdateId());
    }
}
