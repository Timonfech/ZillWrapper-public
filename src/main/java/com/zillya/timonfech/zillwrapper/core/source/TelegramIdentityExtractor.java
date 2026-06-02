package com.zillya.timonfech.zillwrapper.core.source;

import com.zillya.timonfech.zillwrapper.core.entities.security.*;
import com.zillya.timonfech.zillwrapper.core.exceptions.AuthenticationException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.EnumMap;
import java.util.Map;

@Component
public class TelegramIdentityExtractor implements IdentityExtractor<TelegramInboundEvent> {

    @Override
    public Identity extract(TelegramInboundEvent event) {
        User from = null;
        Message message = event.getPayload().getMessage();
        if (message != null) {
            from = message.getFrom();
        } else if (event.getPayload().hasEditedMessage()) {
            Message editedMessage = event.getPayload().getEditedMessage();
            from = editedMessage != null ? editedMessage.getFrom() : null;
        } else if (event.getPayload().hasCallbackQuery()) {
            CallbackQuery callback = event.getPayload().getCallbackQuery();
            from = callback != null ? callback.getFrom() : null;
        }

        if (from == null || from.getId() == null) {
            throw new AuthenticationException(
                    AuthErrorReason.MISSING_FACTORS,
                    event.getSourceEntity() != null ? event.getSourceEntity().getId() : null,
                    "Telegram update has no user id"
            );
        }

        Map<UserSourceEntity.SecurityFactor, String> factors = new EnumMap<>(UserSourceEntity.SecurityFactor.class);
        factors.put(UserSourceEntity.SecurityFactor.TELEGRAM_ID, String.valueOf(from.getId()));
        if (from.getUserName() != null && !from.getUserName().isBlank()) {
            factors.put(UserSourceEntity.SecurityFactor.TELEGRAM_NICKNAME, from.getUserName());
        }

        return new BaseIdentity(event.getSourceEntity().getId(), SourceType.TELEGRAM, factors);
    }

    @Override
    public SourceType sourceType() {
        return SourceType.TELEGRAM;
    }
}
