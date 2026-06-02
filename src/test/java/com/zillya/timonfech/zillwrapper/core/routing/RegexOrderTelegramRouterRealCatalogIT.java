package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderTextParser;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.telegram.commands.TelegramCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration-style setup for future router/parser tests with real product catalog
 * loaded from src/main/resources/products.yaml via ProductCatalogSyncService.
 *
 * Intentionally contains no @Test methods yet.
 */
@SpringBootTest(properties = {
        "users.sync.enabled=false",
        "telegram.bot.zill.bot-token=test-token",
        "telegram.bot.zill.bot-name=test-bot"
})
class RegexOrderTelegramRouterRealCatalogIT {

    @Autowired
    protected ProductRegistry productRegistry;

    @Autowired
    protected OrderTextParser orderTextParser;

    @Autowired
    protected List<TelegramCommand> availableCommands;

    protected RegexOrderTelegramRouter newRouter() {
        return new RegexOrderTelegramRouter(orderTextParser, availableCommands);
    }

    protected TelegramInboundEvent event(String text) {
        Chat chat = new Chat();
        chat.setId(100L);

        Message message = new Message();
        message.setText(text);
        message.setChat(chat);
        message.setEntities(new ArrayList<MessageEntity>());

        Update update = new Update();
        update.setMessage(message);

        return new TelegramInboundEvent(
                new com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity(1L, SourceType.TELEGRAM, "test"),
                update
        );
    }
}

