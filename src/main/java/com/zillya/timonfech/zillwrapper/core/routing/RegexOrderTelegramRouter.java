package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.exceptions.RoutingException;
import com.zillya.timonfech.zillwrapper.core.interactions.commands.CommandIntent;
import com.zillya.timonfech.zillwrapper.core.regex.order.*;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import com.zillya.timonfech.zillwrapper.core.transport.telegram.commands.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class RegexOrderTelegramRouter implements IntentRouter<TelegramInboundEvent> {

    private static final Pattern ID_EXTRACTOR_PATTERN = Pattern.compile("(?:wid\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);

    private final OrderTextParser orderTextParser;
    private final List<TelegramCommand> availableCommands;

    @Override
    public boolean canRoute(InboundEvent<?> event) {
        if (!(event instanceof TelegramInboundEvent tgEvent)) {
            return false;
        }
        Update update = tgEvent.getPayload();
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        Message message = update.getMessage();
        boolean hasCommand = hasBotCommand(message) || looksLikeCommandText(message.getText());
        boolean looksLikeOrder = orderTextParser.looksLikeStructuredOrder(message.getText());
        boolean result = hasCommand || looksLikeOrder;
        log.info("Router canRoute: result={} hasCommand={} looksLikeOrder={} chatId={} messageId={} text='{}'",
                result,
                hasCommand,
                looksLikeOrder,
                message.getChatId(),
                message.getMessageId(),
                safeSnippet(message.getText()));
        return result;
    }

    @Override
    public RoutingDecision route(TelegramInboundEvent event) {
        Message message = event.getPayload().getMessage();
        String text = message.getText();

        if (looksLikeCommandText(text) || hasBotCommand(message)) {
            log.info("Router route: command path selected chatId={} messageId={} text='{}'",
                    message.getChatId(),
                    message.getMessageId(),
                    safeSnippet(text));
            Optional<MessageEntity> cmdEntity = message.getEntities().stream()
                    .filter(e -> "bot_command".equals(e.getType()))
                    .findFirst();
            if (cmdEntity.isPresent()) {
                OrderOperationContext ctx = handleCommand(event, message, cmdEntity.get());
                if (ctx.getCurrentStage() == OperationType.RESEND_NOTIFICATION && message.getReplyToMessage() != null) {
                    return new RoutingDecision.StartPipelineDecision(ctx);
                }
                return isSearchLike(ctx)
                        ? new RoutingDecision.SearchDecision(toCommandIntent(event, message, ctx))
                        : new RoutingDecision.StartPipelineDecision(ctx);
            }
            OrderOperationContext ctx = handleCommandFallback(event, message);
            if (ctx.getCurrentStage() == OperationType.RESEND_NOTIFICATION && message.getReplyToMessage() != null) {
                return new RoutingDecision.StartPipelineDecision(ctx);
            }
            return isSearchLike(ctx)
                    ? new RoutingDecision.SearchDecision(toCommandIntent(event, message, ctx))
                    : new RoutingDecision.StartPipelineDecision(ctx);
        }

        if (!orderTextParser.looksLikeStructuredOrder(text)) {
            log.info("Router route: ignored non-structured text chatId={} messageId={} text='{}'",
                    message.getChatId(),
                    message.getMessageId(),
                    safeSnippet(text));
            return new RoutingDecision.IgnoreDecision("Message does not match order structure");
        }

        log.info("Router route: order preview path selected chatId={} messageId={}", message.getChatId(), message.getMessageId());
        OrderOperationContext context = parseFullOrder(event, text.trim(), OperationType.ORDER_CREATION);
        return new RoutingDecision.PreviewDecision(event, context);
    }

    private OrderOperationContext handleCommand(TelegramInboundEvent event, Message message, MessageEntity entity) {
        String fullText = message.getText();
        String cmdRaw = fullText.substring(entity.getOffset(), entity.getOffset() + entity.getLength()).split("@")[0];
        String cleanCmd = cmdRaw.startsWith("/")
                ? cmdRaw.substring(1).toLowerCase(Locale.ROOT)
                : cmdRaw.toLowerCase(Locale.ROOT);

        TelegramCommand cmd = availableCommands.stream()
                .filter(c -> c.matches(cleanCmd))
                .findFirst()
                .orElseThrow(() -> new RoutingException("Unsupported command: " + cmdRaw, false));
        String payload = fullText.substring(entity.getOffset() + entity.getLength()).trim();
        payload = decorateModifyStatusPayload(cmd, payload);
        log.info("Router command resolved via entity: raw={} clean={} target={} chatId={} messageId={} replyTo={}",
                cmdRaw,
                cleanCmd,
                cmd.getTargetOperationType(),
                message.getChatId(),
                message.getMessageId(),
                message.getReplyToMessage() != null ? message.getReplyToMessage().getMessageId() : null);

        if (message.getReplyToMessage() != null && message.getReplyToMessage().hasText()) {
            String originalText = message.getReplyToMessage().getText();
            if (orderTextParser.looksLikeStructuredOrder(originalText)) {
                OrderOperationContext ctx = parseFullOrder(event, originalText, cmd.getTargetOperationType());
                ctx.setOrderId(null);
                ctx.setCommandPayload(payload);
                ctx.setLocaleTag(resolveLocaleTag(null, message));
                return ctx;
            }
        }

        Long id = extractId(payload);
        log.info("Router command payload parsed via entity: payload='{}' id={} target={}",
                safeSnippet(payload),
                id,
                cmd.getTargetOperationType());
        if (id == null && isResendCommand(cmd) && message.getReplyToMessage() != null) {
            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    event
            );
            opCtx.setCurrentStage(cmd.getTargetOperationType());
            opCtx.setCommandPayload(payload);
            opCtx.setLocaleTag(resolveLocaleTag(null, message));
            return opCtx;
        }
        if (cmd.getTargetOperationType() == OperationType.LICENSE_SEARCH) {
            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    event
            );
            opCtx.setCurrentStage(cmd.getTargetOperationType());
            opCtx.setCommandPayload(payload);
            opCtx.setLocaleTag(resolveLocaleTag(null, message));
            return opCtx;
        }
        if (id == null
                && (cmd.getTargetOperationType() == OperationType.SUSPENSION
                || cmd.getTargetOperationType() == OperationType.DETACH_ACTIVATIONS
                || cmd.getTargetOperationType() == OperationType.MODIFY_STATUS
                || cmd.getTargetOperationType() == OperationType.RESEND_NOTIFICATION)) {
            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    event
            );
            opCtx.setCurrentStage(cmd.getTargetOperationType());
            opCtx.setCommandPayload(payload);
            opCtx.setLocaleTag(resolveLocaleTag(null, message));
            return opCtx;
        }
        if (id == null) {
            throw new RoutingException("ID required for command " + cmdRaw + ". Example: /resend 95470", false);
        }

        OrderOperationContext opCtx = new OrderOperationContext(
                event.getSourceEntity().getId(),
                id,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                event
        );
        opCtx.setCurrentStage(cmd.getTargetOperationType());
        opCtx.setCommandPayload(payload);
        opCtx.setLocaleTag(resolveLocaleTag(null, message));
        return opCtx;
    }

    private boolean isResendCommand(TelegramCommand cmd) {
        return cmd.getTargetOperationType() == OperationType.RESEND_NOTIFICATION;
    }

    private boolean isSearchLike(OrderOperationContext ctx) {
        OperationType type = ctx.getCurrentStage();
        if (type == OperationType.RESEND_NOTIFICATION && ctx.getOrderId() != null) {
            return false;
        }
        return type == OperationType.LICENSE_SEARCH
                || type == OperationType.MODIFY_STATUS
                || type == OperationType.DETACH_ACTIVATIONS
                || type == OperationType.RESEND_NOTIFICATION
                || type == OperationType.SUSPENSION;
    }

    private OrderOperationContext parseFullOrder(TelegramInboundEvent event, String text, OperationType type) {
        try {
            ParsedOrderRequest parsed = orderTextParser.tryParse(text)
                    .orElseThrow(() -> new RoutingException("Message does not match order structure", false));
            ParsedOrderReference reference = parsed.reference();
            List<OrderItemSpec> itemSpecs = toItemSpecs(parsed.items());

            boolean hasExcel = itemSpecs.stream()
                    .anyMatch(spec -> spec.outputTypes().contains(OutputType.EXCEL));

            List<String> emails = (parsed.emails() == null || parsed.emails().isEmpty())
                    ? List.of(parsed.email())
                    : parsed.emails();
            List<DeliveryTargetSpec> deliveryTargets = emails.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> new DeliveryTargetSpec(
                            ContactMethodType.EMAIL,
                            v,
                            hasExcel ? OutputType.EXCEL : OutputType.TEXT
                    ))
                    .distinct()
                    .toList();

            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    reference.portalId(),
                    parsed.email(),
                    itemSpecs,
                    deliveryTargets,
                    event
            );
            opCtx.setEmails(emails);
            opCtx.setWhiteAdminId(reference.whiteAdminId());
            opCtx.setUserComment(reference.userComment());
            opCtx.setWaDocAddress(reference.docAddress());
            opCtx.setWaComment(reference.waComment());
            opCtx.setPartnerOverride(parsed.partner());
            opCtx.setCurrentStage(type);
            if (type == OperationType.ORDER_CREATION) {
                // Telegram orders are treated as already paid and should pass PAYED gate.
                opCtx.setPayedReady(true);
                opCtx.setIncludeLegacySync(reference.whiteAdminId() != null);
            }
            opCtx.setLocaleTag(resolveLocaleTag(parsed.localeTag(), event.getPayload().getMessage()));
            return opCtx;
        } catch (RoutingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RoutingException("Order parsing error: " + ex.getMessage(), false);
        }
    }

    private List<OrderItemSpec> toItemSpecs(List<ParsedOrderItem> parsedItems) {
        List<OrderItemSpec> itemSpecs = new ArrayList<>();
        for (ParsedOrderItem item : parsedItems) {
            itemSpecs.add(new OrderItemSpec(
                    item.product(),
                    item.count(),
                    item.period(),
                    item.computers(),
                    item.outputTypes(),
                    item.keyTypes(),
                    item.subscribed(),
                    item.options()
            ));
        }
        return itemSpecs;
    }

    private Long extractId(String text) {
        Matcher matcher = ID_EXTRACTOR_PATTERN.matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private OrderOperationContext handleCommandFallback(TelegramInboundEvent event, Message message) {
        String fullText = message.getText() == null ? "" : message.getText().trim();
        if (fullText.isBlank() || !fullText.startsWith("/")) {
            throw new RoutingException("Unsupported command format", false);
        }
        String firstToken = fullText.split("\\s+")[0];
        String cmdRaw = firstToken.split("@")[0];
        String cleanCmd = cmdRaw.startsWith("/")
                ? cmdRaw.substring(1).toLowerCase(Locale.ROOT)
                : cmdRaw.toLowerCase(Locale.ROOT);
        TelegramCommand cmd = availableCommands.stream()
                .filter(c -> c.matches(cleanCmd))
                .findFirst()
                .orElseThrow(() -> new RoutingException("Unsupported command: " + cmdRaw, false));
        log.info("Router command resolved via fallback: raw={} clean={} target={} chatId={} messageId={} replyTo={}",
                cmdRaw,
                cleanCmd,
                cmd.getTargetOperationType(),
                message.getChatId(),
                message.getMessageId(),
                message.getReplyToMessage() != null ? message.getReplyToMessage().getMessageId() : null);

        String payload = fullText.length() > firstToken.length()
                ? fullText.substring(firstToken.length()).trim()
                : "";
        payload = decorateModifyStatusPayload(cmd, payload);
        if (message.getReplyToMessage() != null && message.getReplyToMessage().hasText()) {
            String originalText = message.getReplyToMessage().getText();
            if (orderTextParser.looksLikeStructuredOrder(originalText)) {
                OrderOperationContext ctx = parseFullOrder(event, originalText, cmd.getTargetOperationType());
                ctx.setOrderId(null);
                ctx.setCommandPayload(payload);
                ctx.setLocaleTag(resolveLocaleTag(null, message));
                return ctx;
            }
            if (cmd.getTargetOperationType() == OperationType.RESEND_NOTIFICATION && payload.isBlank()) {
                OrderOperationContext opCtx = new OrderOperationContext(
                        event.getSourceEntity().getId(),
                        null,
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        event
                );
                opCtx.setCurrentStage(cmd.getTargetOperationType());
                opCtx.setCommandPayload(payload);
                opCtx.setLocaleTag(resolveLocaleTag(null, message));
                return opCtx;
            }
        }

        Long id = extractId(payload);
        log.info("Router command payload parsed via fallback: payload='{}' id={} target={}",
                safeSnippet(payload),
                id,
                cmd.getTargetOperationType());
        if (id == null
                && (cmd.getTargetOperationType() == OperationType.SUSPENSION
                || cmd.getTargetOperationType() == OperationType.DETACH_ACTIVATIONS
                || cmd.getTargetOperationType() == OperationType.MODIFY_STATUS
                || cmd.getTargetOperationType() == OperationType.RESEND_NOTIFICATION)) {
            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    event
            );
            opCtx.setCurrentStage(cmd.getTargetOperationType());
            opCtx.setCommandPayload(payload);
            opCtx.setLocaleTag(resolveLocaleTag(null, message));
            return opCtx;
        }
        if (cmd.getTargetOperationType() == OperationType.LICENSE_SEARCH) {
            OrderOperationContext opCtx = new OrderOperationContext(
                    event.getSourceEntity().getId(),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    event
            );
            opCtx.setCurrentStage(cmd.getTargetOperationType());
            opCtx.setCommandPayload(payload);
            opCtx.setLocaleTag(resolveLocaleTag(null, message));
            return opCtx;
        }
        if (id == null) {
            throw new RoutingException("ID required for command " + cmdRaw + ". Example: /resend 95470", false);
        }
        OrderOperationContext opCtx = new OrderOperationContext(
                event.getSourceEntity().getId(),
                id,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                event
        );
        opCtx.setCurrentStage(cmd.getTargetOperationType());
        opCtx.setCommandPayload(payload);
        opCtx.setLocaleTag(resolveLocaleTag(null, message));
        return opCtx;
    }

    private boolean hasBotCommand(Message message) {
        return message.hasEntities()
                && message.getEntities().stream().anyMatch(e -> "bot_command".equals(e.getType()));
    }

    private boolean looksLikeCommandText(String text) {
        return text != null && text.trim().startsWith("/");
    }

    private String safeSnippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String resolveLocaleTag(String parsedLocaleTag, Message message) {
        if (parsedLocaleTag != null && !parsedLocaleTag.isBlank()) {
            return parsedLocaleTag.trim().toLowerCase(Locale.ROOT);
        }
        return "uk";
    }

    private CommandIntent toCommandIntent(TelegramInboundEvent event, Message message, OrderOperationContext ctx) {
        Integer replyTo = message.getReplyToMessage() != null ? message.getReplyToMessage().getMessageId() : null;
        Long actor = message.getFrom() != null ? message.getFrom().getId() : null;
        return CommandIntent.builder()
                .sourceId(event.getSourceEntity().getId())
                .operationType(ctx.getCurrentStage())
                .payload(ctx.getCommandPayload())
                .chatId(message.getChatId())
                .messageThreadId(message.getMessageThreadId())
                .messageId(message.getMessageId())
                .replyToMessageId(replyTo)
                .actorUserId(actor)
                .build();
    }

    private String decorateModifyStatusPayload(TelegramCommand cmd, String payload) {
        if (cmd.getTargetOperationType() != OperationType.MODIFY_STATUS) {
            return payload;
        }
        String normalized = payload == null ? "" : payload.trim();
        if (normalized.matches("(?i).*(^|\\s)status\\s*=\\s*[^\\s]+.*")) {
            return normalized;
        }
        String statusValue = "blocked";
        if ("allow".equalsIgnoreCase(cmd.getName())) {
            statusValue = "allow";
        } else if ("block".equalsIgnoreCase(cmd.getName())) {
            statusValue = "blocked";
        }
        if (normalized.isBlank()) {
            return "status=" + statusValue;
        }
        return "status=" + statusValue + " " + normalized;
    }
}
