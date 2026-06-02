package com.zillya.timonfech.zillwrapper.core.transport;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Explicit polling registration to avoid relying on starter autoconfiguration behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotPollingRegistrar {

    private final ZillyaTelegramBot bot;

    @PostConstruct
    void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Telegram bot polling registered explicitly for botName={}", bot.getBotUsername());
            var me = bot.execute(new GetMe());
            log.info("Telegram bot API check OK: id={}, username=@{}", me.getId(), me.getUserName());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot polling: {}", e.getMessage(), e);
        }
    }
}
