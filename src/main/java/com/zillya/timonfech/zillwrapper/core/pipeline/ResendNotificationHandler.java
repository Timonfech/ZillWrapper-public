package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.aspects.OperationStep;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.interfaces.OperationHandler;
import com.zillya.timonfech.zillwrapper.core.pipeline.resend.ResendLookupContext;
import com.zillya.timonfech.zillwrapper.core.pipeline.resend.ResendResolveResult;
import com.zillya.timonfech.zillwrapper.core.pipeline.resend.ResendResolveStatus;
import com.zillya.timonfech.zillwrapper.core.pipeline.resend.ResendTargetResolver;
import com.zillya.timonfech.zillwrapper.core.regex.flags.*;
import com.zillya.timonfech.zillwrapper.core.regex.order.KeyTypeAliasParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderReferenceLineParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderTextParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderReference;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves an existing order for resend flow and passes orderId to next stages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResendNotificationHandler implements OperationHandler<IOperationContext> {
    private final List<ResendTargetResolver> resolvers;
    private final OrderTextParser orderTextParser;
    private final OrderReferenceLineParser orderReferenceLineParser;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final LicenseRepository licenseRepository;
    private final KeyTypeAliasParser keyTypeAliasParser;
    @Qualifier("orderFlagParser")
    private final FlagParser orderFlagParser;

    private static final Pattern RESEND_KEY_TYPES_PATTERN = Pattern.compile(
            "(?<keyTypes>"
                    + KeyTypeAliasParser.TOKEN_PATTERN
                    + "(?:\\s*(?:" + KeyTypeAliasParser.SEPARATOR_PATTERN + ")\\s*(?:" + KeyTypeAliasParser.TOKEN_PATTERN + "))*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    @Override
    public String name() {
        return "RESEND_NOTIFICATION";
    }

    @Override
    public OperationType handledOperationType() {
        return OperationType.RESEND_NOTIFICATION;
    }

    @Override
    public Class<? extends IOperationContext> requiredContextType() {
        return OrderOperationContext.class;
    }

    @Override
    public boolean supports(IOperationContext context) {
        return context.getOperationType() == OperationType.RESEND_NOTIFICATION
                && context instanceof OrderOperationContext;
    }

    @Override
    @OperationStep(type = OperationType.RESEND_NOTIFICATION, stepProps = {OperationStep.Props.START})
    public OperationResult<?> handle(IOperationContext context) {
        OrderOperationContext orderCtx = asOrderContext(context).orElse(null);
        if (orderCtx == null) {
            return OperationResult.fail("RESEND_NOTIFICATION requires OrderOperationContext", false);
        }
        log.info("Resend handler invoked: opId={} stageExecId={} portalId={} whiteAdminId={} userComment='{}' orderId={}",
                orderCtx.getOperationId(),
                orderCtx.getStageExecutionId(),
                orderCtx.getPortalId(),
                orderCtx.getWhiteAdminId(),
                orderCtx.getUserComment(),
                orderCtx.getOrderId());

        Optional<ResendTargetResolver> resolverOpt = resolvers.stream()
                .filter(r -> r.supports(com.zillya.timonfech.zillwrapper.EntityTypeEnum.ORDER))
                .findFirst();
        if (resolverOpt.isEmpty()) {
            return OperationResult.fail("No resend resolver configured for ORDER", false);
        }

        ResendLookupContext lookupContext = buildLookupContext(orderCtx);
        log.info("Resend lookup context: explicitOrderId={} portalId={} whiteAdminId={} userComment='{}' chatId={} controlMessageId={} initiatorUserId={}",
                lookupContext.explicitOrderId(),
                lookupContext.portalId(),
                lookupContext.whiteAdminId(),
                lookupContext.userComment(),
                lookupContext.chatId(),
                lookupContext.controlMessageId(),
                lookupContext.initiatorUserId());
        ResendResolveResult result = resolverOpt.get().resolve(lookupContext);
        log.info("Resend resolve result: status={} source={} targetId={} reason={}",
                result.status(),
                result.target() != null ? result.target().source() : null,
                result.target() != null ? result.target().entityId() : null,
                result.reason());
        if (result.status() == ResendResolveStatus.NOT_FOUND) {
            log.warn("Resend resolve failed: NOT_FOUND, reason={}", result.reason());
            return OperationResult.fail("Order not found for resend: " + result.reason(), false);
        }
        if (result.status() == ResendResolveStatus.AMBIGUOUS) {
            log.warn("Resend resolve failed: AMBIGUOUS, reason={}", result.reason());
            return OperationResult.fail("Resend reference is ambiguous: " + result.reason(), false);
        }

        Long resolvedOrderId = result.target().entityId();
        orderCtx.setOrderId(resolvedOrderId);
        var orderOpt = orderRepository.findByIdWithClient(resolvedOrderId);
        if (orderOpt.isEmpty()) {
            return OperationResult.fail("Resolved order not found: " + resolvedOrderId, false);
        }
        orderOpt.map(order -> order.getClient() != null ? order.getClient().getLocale() : null)
                .filter(locale -> locale != null && !locale.toLanguageTag().isBlank())
                .ifPresent(locale -> orderCtx.setLocaleTag(locale.toLanguageTag()));

        ResendOverrides override = parseOverrides(orderCtx.getCommandPayload());
        if (!override.unsupportedFlags().isEmpty()) {
            return OperationResult.fail("Unsupported resend flags: " + String.join(", ", override.unsupportedFlags())
                    + ". Allowed in resend: key type aliases (online/offline) and output flags (-e/-ne/-t).", false);
        }
        List<OrderItemEntity> orderItems = orderItemRepository.findByOrderIdOrderByIdAsc(resolvedOrderId);
        if (orderItems.isEmpty()) {
            return OperationResult.fail("Order has no items for resend: " + resolvedOrderId, false);
        }
        Long scopedOrderItemId = parseLongToken(orderCtx.getCommandPayload(), "rid_order_item_id");
        Long scopedLicenseId = parseLongToken(orderCtx.getCommandPayload(), "rid_license_id");
        if (!hasKeysForScope(resolvedOrderId, scopedOrderItemId, scopedLicenseId)) {
            return OperationResult.fail("No keys found for this order/scope: orderId=" + resolvedOrderId, false);
        }
        if (override.hasAny()) {
            for (OrderItemEntity item : orderItems) {
                if (scopedOrderItemId != null && !scopedOrderItemId.equals(item.getId())) {
                    continue;
                }
                if (!override.addedKeyTypes().isEmpty()) {
                    List<KeyType> existing = item.getKeyTypes() == null ? List.of() : item.getKeyTypes();
                    EnumSet<KeyType> mergedKeyTypes = EnumSet.noneOf(KeyType.class);
                    mergedKeyTypes.addAll(existing);
                    mergedKeyTypes.addAll(override.addedKeyTypes());
                    item.setKeyTypes(new ArrayList<>(mergedKeyTypes));
                }
                if (override.excelFlag() != null || override.textFlag() != null) {
                    List<OutputType> existingOut = item.getOutputTypes() == null ? List.of() : item.getOutputTypes();
                    EnumSet<OutputType> mergedOut = EnumSet.noneOf(OutputType.class);
                    mergedOut.addAll(existingOut);
                    if (Boolean.TRUE.equals(override.excelFlag())) {
                        mergedOut.add(OutputType.EXCEL);
                    } else if (Boolean.FALSE.equals(override.excelFlag())) {
                        mergedOut.remove(OutputType.EXCEL);
                    }
                    if (Boolean.TRUE.equals(override.textFlag())) {
                        mergedOut.add(OutputType.TEXT);
                    }
                    item.setOutputTypes(new ArrayList<>(mergedOut));
                }
            }
            orderItemRepository.saveAll(orderItems);
            log.info("Resend overrides applied orderId={} addKeyTypes={} excelFlag={} textFlag={} items={}",
                    resolvedOrderId,
                    override.addedKeyTypes(),
                    override.excelFlag(),
                    override.textFlag(),
                    orderItems.size());
        }
        log.info("Resolved resend target to order {} via source={}", resolvedOrderId, result.target().source());
        return OperationResult.ok(null);
    }

    private boolean hasKeysForScope(Long orderId, Long scopedOrderItemId, Long scopedLicenseId) {
        if (scopedLicenseId != null) {
            return licenseRepository.findById(scopedLicenseId)
                    .filter(l -> l.getOrderId() != null && l.getOrderId().equals(orderId))
                    .map(this::hasAnyKeyValue)
                    .orElse(false);
        }
        if (scopedOrderItemId != null) {
            return licenseRepository.findByOrderItemId(scopedOrderItemId).stream().anyMatch(this::hasAnyKeyValue);
        }
        return licenseRepository.findByOrderId(orderId).stream().anyMatch(this::hasAnyKeyValue);
    }

    private boolean hasAnyKeyValue(LicenseEntity license) {
        if (license == null || license.getKey() == null) {
            return false;
        }
        String online = license.getKey().getOnlineKey();
        String offline = license.getKey().getOfflineKey();
        return (online != null && !online.isBlank()) || (offline != null && !offline.isBlank());
    }

    private ResendOverrides parseOverrides(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ResendOverrides(List.of(), null, null, List.of());
        }
        List<KeyType> addedTypes = parseKeyTypeOverrides(payload);
        var flags = orderFlagParser.findKnownFlags(payload);
        Boolean excelFlag = null;
        Boolean textFlag = null;
        List<String> unsupported = new ArrayList<>();
        for (var match : flags) {
            if (ExcelFlagDefinition.KEY.equals(match.key())) {
                excelFlag = match.value();
            } else if (TextFlagDefinition.KEY.equals(match.key())) {
                textFlag = match.value();
            } else if (SubscribeFlagDefinition.KEY.equals(match.key())
                    || SubscribeDetailedFlagDefinition.KEY.equals(match.key())
                    || NotifyClientFlagDefinition.KEY.equals(match.key())) {
                unsupported.add(match.key());
            }
        }
        return new ResendOverrides(addedTypes, excelFlag, textFlag, List.copyOf(unsupported));
    }

    private List<KeyType> parseKeyTypeOverrides(String payload) {
        EnumSet<KeyType> keyTypes = EnumSet.noneOf(KeyType.class);
        Matcher matcher = RESEND_KEY_TYPES_PATTERN.matcher(payload.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            keyTypes.addAll(keyTypeAliasParser.parse(matcher.group("keyTypes")));
        }
        return List.copyOf(keyTypes);
    }

    private Long parseLongToken(String payload, String key) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*(\\d+)(?:\\s|$)")
                .matcher(payload);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (Exception ex) {
            return null;
        }
    }

    private record ResendOverrides(List<KeyType> addedKeyTypes,
                                   Boolean excelFlag,
                                   Boolean textFlag,
                                   List<String> unsupportedFlags) {
        boolean hasAny() {
            return !addedKeyTypes.isEmpty() || excelFlag != null || textFlag != null;
        }
    }

    private ResendLookupContext buildLookupContext(OrderOperationContext orderCtx) {
        Long chatId = null;
        Integer controlMessageId = null;
        Long portalId = orderCtx.getPortalId();
        Long whiteAdminId = orderCtx.getWhiteAdminId();
        String userComment = orderCtx.getUserComment();
        if (orderCtx.getSourceContext() instanceof TelegramInboundEvent tgEvent) {
            Message message = tgEvent.getPayload().getMessage();
            if (message != null) {
                chatId = message.getChatId();
                if (message.getReplyToMessage() != null) {
                    controlMessageId = message.getReplyToMessage().getMessageId();
                    log.info("Resend reply context: chatId={} messageId={} replyToMessageId={} replyText='{}'",
                            chatId,
                            message.getMessageId(),
                            controlMessageId,
                            safeSnippet(message.getReplyToMessage().getText()));
                    ParsedOrderReference parsed = parseReplyReference(message.getReplyToMessage().getText());
                    if (parsed != null) {
                        log.info("Resend reply parsed reference: portalId={} whiteAdminId={} userComment='{}'",
                                parsed.portalId(),
                                parsed.whiteAdminId(),
                                parsed.userComment());
                        if (portalId == null) {
                            portalId = parsed.portalId();
                        }
                        if (whiteAdminId == null) {
                            whiteAdminId = parsed.whiteAdminId();
                        }
                        if ((userComment == null || userComment.isBlank()) && parsed.userComment() != null) {
                            userComment = parsed.userComment();
                        }
                    }
                } else {
                    log.info("Resend message has no replyToMessage: chatId={} messageId={}", chatId, message.getMessageId());
                }
            }
        }
        return new ResendLookupContext(
                com.zillya.timonfech.zillwrapper.EntityTypeEnum.ORDER,
                orderCtx.getOrderId(),
                portalId,
                whiteAdminId,
                portalId != null ? String.valueOf(portalId) : null,
                userComment,
                chatId,
                controlMessageId,
                orderCtx.getInitiatorUserId()
        );
    }

    private ParsedOrderReference parseReplyReference(String replyText) {
        if (replyText == null || replyText.isBlank()) {
            return null;
        }
        try {
            Optional<com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderRequest> parsedOrder = orderTextParser.tryParse(replyText);
            if (parsedOrder.isPresent()) {
                log.info("Resend reply parsed as structured order reference");
                return parsedOrder.get().reference();
            }
        } catch (Exception ignored) {
            // fallback to first-line reference parsing below
        }
        String firstLine = replyText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(null);
        if (firstLine == null || !orderReferenceLineParser.matches(firstLine)) {
            return null;
        }
        try {
            return orderReferenceLineParser.parse(firstLine);
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeSnippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160) + "...";
    }
}
