package com.zillya.timonfech.zillwrapper.core.transport;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BotShutdownNotifier {

    @PreDestroy
    public void onShutdown() {
        log.warn("Telegram bot is shutting down. Stopping polling and closing application context.");
    }
}

