package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.communication.TelegramTextRenderer;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.interactions.commands.*;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.search.SearchSession;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSearchActionOrchestrator implements SourceCommandAdapter<Message>, SourceInteractionPresenter {
    private final AbsSender telegramSender;
    private final CommandInteractionService commandInteractionService;
    private final TelegramTextRenderer telegramTextRenderer;
    @Value("${telegram.render.html.cards.enabled:false}")
    private boolean htmlCardsEnabled;

    public boolean handleSearchCommand(TelegramInboundEvent event, UserEntity user, OrderOperationContext ctx) {
        if (event.getPayload() == null || !event.getPayload().hasMessage()) {
            return false;
        }
        Message message = event.getPayload().getMessage();
        CommandIntent intent = toIntent(message, user.getId(), ctx.getSourceId(), ctx.getCommandPayload(), ctx.getCurrentStage());
        InteractionOutcome outcome = commandInteractionService.open(intent);
        return presentOutcome(outcome, null, message.getChatId(), message.getMessageThreadId(), null);
    }

    public boolean handleSearchIntent(CommandIntent intent, UserEntity user) {
        InteractionOutcome outcome = commandInteractionService.open(intent);
        return presentOutcome(outcome, null, intent.chatId(), intent.messageThreadId(), null);
    }

    public boolean handleSearchCallback(CallbackQuery query, UserEntity user) {
        if (query == null || query.getData() == null || !query.getData().startsWith("search_")) {
            return false;
        }
        String[] parts = query.getData().split(":");
        if (parts.length < 2) {
            answer(query.getId(), "Invalid search callback.");
            return true;
        }
        InteractionAction action = toAction(parts[0]);
        String sessionId = parts[1];
        Long actorId = user != null ? user.getId() : null;
        Long chatId = query.getMessage() != null ? query.getMessage().getChatId() : null;
        Integer threadId = null;
        if (query.getMessage() instanceof Message callbackMessage) {
            threadId = callbackMessage.getMessageThreadId();
        }
        Integer editMessageId = query.getMessage() != null ? query.getMessage().getMessageId() : null;

        InteractionOutcome outcome = commandInteractionService.apply(sessionId, actorId, action);
        if (action == InteractionAction.CANCEL) {
            // UX rule for interactive search actions: on cancel, remove the view message
            // and do not send an extra "Cancelled." chat message.
            if (chatId != null && editMessageId != null) {
                try {
                    telegramSender.execute(DeleteMessage.builder()
                            .chatId(chatId.toString())
                            .messageId(editMessageId)
                            .build());
                } catch (TelegramApiException ex) {
                    try {
                        telegramSender.execute(EditMessageReplyMarkup.builder()
                                .chatId(chatId.toString())
                                .messageId(editMessageId)
                                .replyMarkup(null)
                                .build());
                    } catch (TelegramApiException ignore) {
                        log.debug("Failed to clean up cancelled search view chat={} message={}: {}",
                                chatId,
                                editMessageId,
                                ignore.getMessage());
                    }
                }
            }
            answer(query.getId(), null);
            return true;
        }
        boolean handled = presentOutcome(outcome, sessionId, chatId, threadId, editMessageId);
        if (handled) {
            answer(query.getId(), null);
        }
        return handled;
    }

    @Scheduled(fixedDelayString = "${interaction.cleanup.fixed-delay-ms:60000}")
    public void cleanupExpiredSessions() {
        for (SearchSession expired : commandInteractionService.expireDueSessions(Instant.now())) {
            onSessionExpired(expired);
        }
    }

    @Override
    public CommandIntent toIntent(Message inbound, Long actorUserId, Long sourceId, String payload, OperationType operationType) {
        Integer replyTo = inbound.getReplyToMessage() != null ? inbound.getReplyToMessage().getMessageId() : null;
        return CommandIntent.builder()
                .sourceId(sourceId)
                .operationType(operationType)
                .payload(payload)
                .chatId(inbound.getChatId())
                .messageThreadId(inbound.getMessageThreadId())
                .messageId(inbound.getMessageId())
                .replyToMessageId(replyTo)
                .actorUserId(actorUserId)
                .build();
    }

    @Override
    public InteractionAction toAction(String callbackAction) {
        return switch (callbackAction) {
            case "search_prev" -> InteractionAction.PREV;
            case "search_next" -> InteractionAction.NEXT;
            case "search_confirm" -> InteractionAction.CONFIRM_CURRENT;
            case "search_confirm_all" -> InteractionAction.CONFIRM_ALL;
            case "search_cancel" -> InteractionAction.CANCEL;
            default -> InteractionAction.CANCEL;
        };
    }

    @Override
    public void presentView(SearchSession session, boolean actionMode, String actionLabel, String sessionId, Integer editMessageId) {
        int total = session.getViews().size();
        int idx = Math.max(0, Math.min(total - 1, session.getPageIndex()));
        session.setPageIndex(idx);
        String title = "Search Result " + (idx + 1) + "/" + total;
        String summary = session.getSummaryView();
        String warning = session.getWarningView();
        String text;
        if (total > 1 && summary != null && !summary.isBlank()) {
            text = summary + "\n\n" + title + "\n\n" + session.getViews().get(idx);
        } else if (total > 1) {
            text = title + "\nFound " + total + " " + session.getEntityType().name().toLowerCase() + " result(s)." + "\n\n" + session.getViews().get(idx);
        } else {
            text = title + "\n\n" + session.getViews().get(idx);
        }
        if (warning != null && !warning.isBlank()) {
            text = warning + "\n\n" + text;
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        if (total > 1) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder().text("◀").callbackData("search_prev:" + sessionId).build());
            row.add(InlineKeyboardButton.builder().text("▶").callbackData("search_next:" + sessionId).build());
            keyboard.add(row);
        }
        if (actionMode) {
            keyboard.add(List.of(
                    InlineKeyboardButton.builder()
                            .text("Confirm " + actionLabel + " (current)")
                            .callbackData("search_confirm:" + sessionId)
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("Confirm " + actionLabel + " (all)")
                            .callbackData("search_confirm_all:" + sessionId)
                            .build()
            ));
            keyboard.add(List.of(
                    InlineKeyboardButton.builder()
                            .text("Cancel")
                            .callbackData("search_cancel:" + sessionId)
                            .build()
            ));
        }
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(keyboard).build();
        boolean htmlEnabled = htmlCardsEnabled
                && (session.getEntityType() == com.zillya.timonfech.zillwrapper.core.search.SearchEntityType.LICENSE
                || session.getEntityType() == com.zillya.timonfech.zillwrapper.core.search.SearchEntityType.ORDER);

        try {
            if (editMessageId != null) {
                EditMessageText.EditMessageTextBuilder builder = EditMessageText.builder()
                        .chatId(session.getChatId().toString())
                        .messageId(editMessageId)
                        .text(text)
                        .replyMarkup(markup);
                if (htmlEnabled) {
                    builder.parseMode(ParseMode.HTML);
                }
                telegramSender.execute(builder.build());
                commandInteractionService.attachUiMessage(sessionId, editMessageId);
            } else {
                SendMessage.SendMessageBuilder builder = SendMessage.builder()
                        .chatId(session.getChatId().toString())
                        .text(text)
                        .replyMarkup(markup);
                if (htmlEnabled) {
                    builder.parseMode(ParseMode.HTML);
                }
                Message sent = telegramSender.execute(builder.build());
                if (sent != null) {
                    commandInteractionService.attachUiMessage(sessionId, sent.getMessageId());
                }
            }
        } catch (TelegramApiException e) {
            if (htmlEnabled && isHtmlParseError(e)) {
                log.warn("html_render_failed_fallback_plain chatId={} messageId={} sessionId={} err={}",
                        session.getChatId(),
                        editMessageId,
                        sessionId,
                        e.getMessage());
                String plainText = telegramTextRenderer.renderPlainFallback(text);
                try {
                    if (editMessageId != null) {
                        telegramSender.execute(EditMessageText.builder()
                                .chatId(session.getChatId().toString())
                                .messageId(editMessageId)
                                .text(plainText)
                                .replyMarkup(markup)
                                .build());
                        commandInteractionService.attachUiMessage(sessionId, editMessageId);
                    } else {
                        Message sent = telegramSender.execute(SendMessage.builder()
                                .chatId(session.getChatId().toString())
                                .text(plainText)
                                .replyMarkup(markup)
                                .build());
                        if (sent != null) {
                            commandInteractionService.attachUiMessage(sessionId, sent.getMessageId());
                        }
                    }
                    return;
                } catch (TelegramApiException fallbackEx) {
                    log.warn("Failed to present search view after HTML fallback: {}", fallbackEx.getMessage());
                    return;
                }
            }
            log.warn("Failed to present search view: {}", e.getMessage());
        }
    }

    @Override
    public void presentMessage(Long chatId, Integer messageThreadId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text);
            if (messageThreadId != null) {
                builder.messageThreadId(messageThreadId);
            }
            telegramSender.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send search message: {}", e.getMessage());
        }
    }

    @Override
    public void onSessionExpired(SearchSession session) {
        if (session.getChatId() == null || session.getUiMessageId() == null) {
            return;
        }
        try {
            telegramSender.execute(DeleteMessage.builder()
                    .chatId(session.getChatId().toString())
                    .messageId(session.getUiMessageId())
                    .build());
        } catch (TelegramApiException ex) {
            try {
                telegramSender.execute(EditMessageReplyMarkup.builder()
                        .chatId(session.getChatId().toString())
                        .messageId(session.getUiMessageId())
                        .replyMarkup(new InlineKeyboardMarkup())
                        .build());
            } catch (TelegramApiException ignore) {
                log.debug("Failed to remove keyboard for expired session: {}", ignore.getMessage());
            }
        }
    }

    private boolean presentOutcome(InteractionOutcome outcome, String sessionId, Long chatId, Integer messageThreadId, Integer editMessageId) {
        if (outcome == null) {
            return false;
        }
        return switch (outcome.type()) {
            case VIEW -> {
                presentView(outcome.session(), outcome.actionMode(), outcome.actionLabel(), sessionId != null ? sessionId : outcome.session().getSessionId(), editMessageId);
                yield true;
            }
            case STARTED -> {
                // For action-confirm flow we reuse the same control message bound to operation.
                // Sending an additional chat message here creates noisy duplicates.
                yield true;
            }
            case ERROR, NO_MATCH -> {
                presentMessage(chatId, messageThreadId, outcome.message());
                yield true;
            }
            case EXPIRED -> {
                presentMessage(chatId, messageThreadId, "Search session expired.");
                yield true;
            }
        };
    }

    private void answer(String callbackId, String text) {
        if (callbackId == null) {
            return;
        }
        AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackId);
        if (text != null && !text.isBlank()) {
            answer.setText(text);
        }
        try {
            telegramSender.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }

    private boolean isHtmlParseError(TelegramApiException ex) {
        String msg = ex == null ? null : ex.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("can't parse entities")
                || lower.contains("cannot parse entities")
                || lower.contains("parse entities");
    }
}
