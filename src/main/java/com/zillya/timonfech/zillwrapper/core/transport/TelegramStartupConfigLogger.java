package com.zillya.timonfech.zillwrapper.core.transport;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TelegramStartupConfigLogger {

    @Value("${telegram.bot.zill.bot-name:}")
    private String botName;

    @Value("${telegram.bot.zill.bot-token:}")
    private String botToken;

    @PostConstruct
    void logTelegramConfig() {
        String masked = mask(botToken);
        log.info("Telegram config at startup: botName='{}', tokenPresent={}, tokenMasked='{}'",
                botName,
                botToken != null && !botToken.isBlank(),
                masked);
    }

    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return "<empty>";
        }
        if (token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}

