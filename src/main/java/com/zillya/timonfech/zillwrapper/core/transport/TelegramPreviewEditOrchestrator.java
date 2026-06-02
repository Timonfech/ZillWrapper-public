package com.zillya.timonfech.zillwrapper.core.transport;

import com.zillya.timonfech.zillwrapper.core.communication.TelegramPreviewStatus;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.events.OrderPreviewPayload;
import com.zillya.timonfech.zillwrapper.core.events.PreviewItem;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderParseException;
import com.zillya.timonfech.zillwrapper.core.regex.order.OrderTextParser;
import com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderItem;
import com.zillya.timonfech.zillwrapper.core.regex.order.ParsedOrderRequest;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramOperationBindingRepository;
import com.zillya.timonfech.zillwrapper.core.routing.DeliveryTargetSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.runtime.OperationRuntimeRegistry;
import com.zillya.timonfech.zillwrapper.core.source.TelegramInboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPreviewEditOrchestrator {
    private static final Pattern EXPLICIT_LOCALE_FLAG_PATTERN = Pattern.compile("(?i)(?:^|\\s)--?(?:l|locale)\\s*(?:=|:|\\s)\\s*([a-z]{2,8}(?:-[a-z0-9]{2,8})*)");

    private final TelegramOperationBindingRepository bindingRepository;
    private final OrderTextParser orderTextParser;
    private final OperationRuntimeRegistry runtimeRegistry;
    private final ObjectMapper objectMapper;
    private final AbsSender telegramSender;
    private final MessageSource messageSource;

    public boolean tryHandleEditedMessage(TelegramInboundEvent event) {
        Update update = event.getPayload();
        if (update == null || !update.hasEditedMessage() || update.getEditedMessage() == null || !update.getEditedMessage().hasText()) {
            return false;
        }
        Long chatId = update.getEditedMessage().getChatId();
        Integer sourceMessageId = update.getEditedMessage().getMessageId();
        if (chatId == null || sourceMessageId == null) {
            return false;
        }
        TelegramOperationBindingEntity binding = resolveEditableBinding(chatId, sourceMessageId);
        if (binding == null) {
            return false;
        }
        boolean editableStatus = TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())
                || TelegramPreviewStatus.PARSE_ERROR.name().equals(binding.getPreviewStatus());
        if (!editableStatus || binding.getActivePreviewId() == null || binding.getPreviewMessageId() == null) {
            return true;
        }

        String editedText = update.getEditedMessage().getText();
        ParsedOrderRequest parsed;
        try {
            parsed = orderTextParser.tryParse(editedText).orElse(null);
        } catch (OrderParseException parseException) {
            applyParseErrorState(binding, chatId, parseException.getMessage());
            return true;
        } catch (Exception ex) {
            applyParseErrorState(binding, chatId, "Unable to parse edited order text.");
            log.warn("Preview edit parse failed chatId={} sourceMessageId={}: {}",
                    chatId,
                    sourceMessageId,
                    ex.getMessage());
            return true;
        }
        if (parsed == null) {
            applyParseErrorState(binding, chatId, "Edited message does not match order format.");
            return true;
        }

        OrderPreviewPayload payload = new OrderPreviewPayload(
                parsed.reference().portalId(),
                parsed.reference().whiteAdminId(),
                parsed.reference().userComment(),
                parsed.email(),
                parsed.emails(),
                resolveLocaleTag(parsed.localeTag()),
                hasExplicitLocaleFlag(editedText),
                parsed.reference().whiteAdminId() == null && parsed.reference().userComment() != null && !parsed.reference().userComment().isBlank(),
                parsed.reference().docAddress(),
                parsed.reference().waComment(),
                parsed.items().stream().map(this::toPreviewItem).toList(),
                List.of(),
                null
        );
        syncRuntimeContext(binding, parsed, event);

        binding.setPreviewPayloadJson(writeJson(payload));
        binding.setSourceMessageHash(null);
        binding.setPreviewStatus(TelegramPreviewStatus.WAITING.name());
        binding.setPreviewCreatedAt(Instant.now());
        binding.setPreviewExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        bindingRepository.save(binding);

        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(binding.getPreviewMessageId())
                    .text(buildPreviewText(payload))
                    .parseMode("HTML")
                    .replyMarkup(buildPreviewKeyboard(binding.getActivePreviewId(), payload))
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to refresh preview message on edited source chat={} msg={}: {}",
                    chatId,
                    binding.getPreviewMessageId(),
                    e.getMessage());
        }
        return true;
    }

    private void applyParseErrorState(TelegramOperationBindingEntity binding, Long chatId, String reason) {
        binding.setPreviewStatus(TelegramPreviewStatus.PARSE_ERROR.name());
        bindingRepository.save(binding);
        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(binding.getPreviewMessageId())
                    .text("Order parsing error: " + reason + "\nEdit the original order message and try again.")
                    .replyMarkup(null)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to render preview parse error chat={} msg={}: {}",
                    chatId,
                    binding.getPreviewMessageId(),
                    e.getMessage());
        }
    }

    private PreviewItem toPreviewItem(ParsedOrderItem item) {
        return new PreviewItem(
                item.product().brandId(),
                item.product().productId(),
                item.product().names().getOrDefault("en_short", "Product"),
                item.count(),
                item.computers(),
                item.period(),
                item.keyTypes(),
                item.outputTypes(),
                item.subscribed(),
                item.options()
        );
    }

    private void syncRuntimeContext(TelegramOperationBindingEntity binding,
                                    ParsedOrderRequest parsed,
                                    TelegramInboundEvent event) {
        if (binding == null || binding.getOperationId() == null || parsed == null) {
            return;
        }
        runtimeRegistry.load(binding.getOperationId()).ifPresent(existing -> {
            List<OrderItemSpec> itemSpecs = parsed.items().stream()
                    .map(item -> new OrderItemSpec(
                            item.product(),
                            item.count(),
                            item.period(),
                            item.computers(),
                            item.outputTypes(),
                            item.keyTypes(),
                            item.subscribed(),
                            item.options()
                    ))
                    .toList();

            boolean hasExcel = itemSpecs.stream().anyMatch(spec -> spec.outputTypes().contains(OutputType.EXCEL));
            List<String> emails = (parsed.emails() == null || parsed.emails().isEmpty())
                    ? List.of(parsed.email())
                    : parsed.emails();
            List<DeliveryTargetSpec> targets = emails.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> new DeliveryTargetSpec(
                            ContactMethodType.EMAIL,
                            v,
                            hasExcel ? OutputType.EXCEL : OutputType.TEXT
                    ))
                    .distinct()
                    .toList();

            OrderOperationContext updated = new OrderOperationContext(
                    existing.getSourceId(),
                    parsed.reference().portalId(),
                    parsed.email(),
                    itemSpecs,
                    targets,
                    event
            );
            updated.setEmails(emails);
            updated.setOperationId(existing.getOperationId());
            updated.setStageExecutionId(existing.getStageExecutionId());
            updated.setCurrentStage(existing.getCurrentStage());
            updated.setInitiatorUserId(existing.getInitiatorUserId());
            updated.setOrderId(existing.getOrderId());
            updated.setPayedReady(existing.isPayedReady());
            updated.setSkipDuplicateCheck(existing.isSkipDuplicateCheck());
            updated.setWhiteAdminId(parsed.reference().whiteAdminId());
            updated.setUserComment(parsed.reference().userComment());
            updated.setWaDocAddress(parsed.reference().docAddress());
            updated.setWaComment(parsed.reference().waComment());
            updated.setPartnerOverride(parsed.partner() != null ? parsed.partner() : existing.getPartnerOverride());
            updated.setLocaleTag(resolveLocaleTag(parsed.localeTag()));
            updated.setIncludeLegacySync(parsed.reference().whiteAdminId() != null);

            runtimeRegistry.save(binding.getOperationId(), updated);
            log.info("Preview edit synchronized runtime context: opId={} chatId={} sourceMessageId={} previewId={} previewMessageId={}",
                    binding.getOperationId(),
                    binding.getChatId(),
                    binding.getSourceMessageId(),
                    binding.getActivePreviewId(),
                    binding.getPreviewMessageId());
        });
    }

    private TelegramOperationBindingEntity resolveEditableBinding(Long chatId, Integer sourceMessageId) {
        List<TelegramOperationBindingEntity> bindings = bindingRepository
                .findAllByChatIdAndSourceMessageIdOrderByPreviewCreatedAtDesc(chatId, sourceMessageId);
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.stream()
                .filter(this::isEditablePreviewBinding)
                .max(Comparator.comparing(b -> b.getPreviewCreatedAt() == null ? Instant.EPOCH : b.getPreviewCreatedAt()))
                .orElse(bindings.getFirst());
    }

    private boolean isEditablePreviewBinding(TelegramOperationBindingEntity binding) {
        if (binding == null) {
            return false;
        }
        boolean editableStatus = TelegramPreviewStatus.WAITING.name().equals(binding.getPreviewStatus())
                || TelegramPreviewStatus.PARSE_ERROR.name().equals(binding.getPreviewStatus());
        return editableStatus && binding.getActivePreviewId() != null && binding.getPreviewMessageId() != null;
    }

    private String resolveLocaleTag(String parsedLocaleTag) {
        if (parsedLocaleTag != null && !parsedLocaleTag.isBlank()) {
            return parsedLocaleTag.trim().toLowerCase(Locale.ROOT);
        }
        return "uk";
    }

    private InlineKeyboardMarkup buildPreviewKeyboard(String previewId, OrderPreviewPayload payload) {
        Locale locale = localeFromPayload(payload);
        if (payload != null && payload.isWaCreateDecisionRequired() && payload.getWhiteAdminId() == null) {
            return InlineKeyboardMarkup.builder().keyboard(List.of(
                    List.of(
                            InlineKeyboardButton.builder().text(msg("telegram.preview.button.create_continue", locale)).callbackData("task_wa_create_confirm:" + previewId).build()
                    ),
                    List.of(InlineKeyboardButton.builder().text(msg("telegram.preview.button.skip_continue", locale)).callbackData("task_wa_skip_confirm:" + previewId).build()),
                    List.of(InlineKeyboardButton.builder().text(msg("telegram.button.cancel", locale)).callbackData("task_cancel:" + previewId).build())
            )).build();
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(
                        InlineKeyboardButton.builder().text(msg("telegram.button.confirm", locale)).callbackData("task_confirm:" + previewId).build(),
                        InlineKeyboardButton.builder().text(msg("telegram.button.cancel", locale)).callbackData("task_cancel:" + previewId).build()
                )))
                .build();
    }

    private String buildPreviewText(OrderPreviewPayload payload) {
        Locale locale = localeFromPayload(payload);
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(msg("telegram.preview.title", locale)).append("</b>").append("\n");
        appendIfPresent(sb, msg("telegram.preview.portal", locale), payload.getPortalId());
        appendIfPresent(sb, "WhiteAdmin", payload.getWhiteAdminId());
        appendIfPresent(sb, msg("telegram.preview.comment", locale), payload.getUserComment());
        appendIfPresent(sb, msg("telegram.preview.wa.address", locale), payload.getWaDocAddress());
        appendIfPresent(sb, msg("telegram.preview.wa.comment", locale), payload.getWaComment());
        if (payload.isWaCreateDecisionRequired() && payload.getWhiteAdminId() == null && payload.getUserComment() != null && !payload.getUserComment().isBlank()) {
            sb.append("\n")
                    .append("<b>").append(msg("telegram.preview.wa.question.title", locale)).append("</b>").append("\n")
                    .append(msg("telegram.preview.wa.question.body", locale))
                    .append("\n");
        }
        String previewEmails = payload.getEmails() == null || payload.getEmails().isEmpty()
                ? payload.getEmail()
                : String.join(", ", payload.getEmails());
        appendIfPresent(sb, msg("telegram.preview.email", locale), previewEmails);
        if (payload.isLocaleExplicit()) {
            appendIfPresent(sb, msg("telegram.preview.locale", locale), payload.getLocaleTag());
        }
        if (payload.getItems() != null && !payload.getItems().isEmpty()) {
            sb.append("<b>").append(msg("telegram.preview.items", locale)).append(":</b> ").append(payload.getItems().size()).append("\n");
        }
        int idx = 1;
        for (PreviewItem item : payload.getItems() == null ? List.<PreviewItem>of() : payload.getItems()) {
            sb.append(idx++).append(". <b>").append(item.getProductName()).append("</b>");
            if (item.getCount() > 0) {
                sb.append(" x").append(item.getCount());
            }
            if (item.getPcPerLicense() > 0) {
                sb.append(", pc=").append(item.getPcPerLicense());
            }
            if (item.getPeriod() != null) {
                sb.append(", period=").append(item.getPeriod().amount()).append(" ").append(item.getPeriod().unit());
            }
            if (item.getKeyTypes() != null && !item.getKeyTypes().isEmpty()) {
                sb.append(", ").append(msg("telegram.preview.keytype", locale)).append("=").append(item.getKeyTypes());
            }
            if (item.getOutputTypes() != null && !item.getOutputTypes().isEmpty()) {
                sb.append(", ").append(msg("telegram.preview.output", locale)).append("=").append(item.getOutputTypes());
            }
            sb.append(", ").append(msg("telegram.preview.subscribe", locale)).append("=")
                    .append(item.isSubscribed() ? msg("telegram.preview.on", locale) : msg("telegram.preview.off", locale));
            if (item.getOptions() != null && item.getOptions().serverNumber() != null) {
                sb.append(", server=").append(item.getOptions().serverNumber());
            }
            sb.append("\n");
        }
        sb.append("\n").append("<i>").append(msg("telegram.preview.confirm_within", locale)).append("</i>");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        sb.append("• ").append(label).append(": ").append("<code>").append(value).append("</code>").append("\n");
    }

    private Locale localeFromPayload(OrderPreviewPayload payload) {
        if (payload == null || payload.getLocaleTag() == null || payload.getLocaleTag().isBlank()) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(payload.getLocaleTag());
    }

    private String msg(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean hasExplicitLocaleFlag(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        return EXPLICIT_LOCALE_FLAG_PATTERN.matcher(rawText).find();
    }
}
