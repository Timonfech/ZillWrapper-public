package com.zillya.timonfech.zillwrapper.core.communication.finalization;

import com.zillya.timonfech.zillwrapper.core.communication.KeyPreviewFormatter;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.pipeline.ExcelLicenseReportGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class TelegramNewOrderFinalPolicy implements FinalNotificationPolicy {

    private final AbsSender telegramSender;
    private final ExcelLicenseReportGenerator excelLicenseReportGenerator;
    private final ProductRegistry productRegistry;
    private final KeyPreviewFormatter keyPreviewFormatter;
    private final MessageSource messageSource;
    @Value("${whiteAdminPanel.target:https://zillya.ua/payment/admin_page.php}")
    private String whiteAdminBaseUrl;
    @Value("${whiteAdminPanel.orders.detailsPage:orders.php}")
    private String whiteAdminOrderDetailsPage;

    @Override
    public String kind() {
        return "NEW_ORDER_RICH";
    }

    @Override
    public boolean supports(FinalNotificationContext context) {
        return context.newOrderCreated()
                && context.orderId() != null
                && context.order() != null;
    }

    @Override
    public void notify(FinalNotificationContext context) {
        Long chatId = context.binding().getChatId();
        if (chatId == null) {
            return;
        }

        List<OrderItemEntity> items = context.items() == null ? List.of() : context.items();
        List<LicenseEntity> licenses = context.licenses() == null ? List.of() : context.licenses();

        boolean hasArtifactDemand = items.stream()
                .anyMatch(i -> i.getOutputTypes() != null && i.getOutputTypes().contains(OutputType.EXCEL));

        boolean sendArtifacts = hasArtifactDemand && !licenses.isEmpty();

        String text = buildFinalText(context, licenses, sendArtifacts);
        upsertControlMessage(chatId, context, text);

        if (!sendArtifacts) {
            return;
        }

        try {
            byte[] excelData = excelLicenseReportGenerator.generateArtifactReport(
                    licenses,
                    items,
                    this::resolveProductName
            );
            String filename = "licenses_" + resolveArtifactReference(context) + ".xlsx";
            telegramSender.execute(SendDocument.builder()
                    .chatId(chatId.toString())
                    .document(new InputFile(new ByteArrayInputStream(excelData), filename))
                    .caption("Artifacts")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send final artifact file for order {} chat={}: {}",
                    context.orderId(),
                    chatId,
                    e.getMessage());
        }
    }

    private String buildFinalText(FinalNotificationContext context,
                                  List<LicenseEntity> licenses,
                                  boolean artifactsSent) {
        StringBuilder sb = new StringBuilder();
        String summaryMessage = context.parentOperation() != null ? context.parentOperation().getErrorMessage() : null;
        boolean awaitingPayment = summaryMessage != null
                && summaryMessage.toLowerCase(Locale.ROOT).contains("awaiting payment");
        if (awaitingPayment) {
            sb.append("Order created. Awaiting payment confirmation.").append("\n");
        } else {
            sb.append("All steps completed successfully").append("\n");
        }
        sb.append("Order #").append(context.orderId()).append("\n");
        appendWhiteAdminOrderLink(sb, context.order(), context.locale());
        List<String> warnings = new ArrayList<>();
        if (context.stageWarnings() != null) {
            warnings.addAll(context.stageWarnings());
        }
        if (context.nonCriticalWarnings() != null) {
            warnings.addAll(context.nonCriticalWarnings());
        }
        if (!warnings.isEmpty()) {
            sb.append("Warnings:").append("\n");
            for (String warning : warnings) {
                sb.append("- ").append(warning).append("\n");
            }
        }

        List<OrderItemEntity> sortedItems = new ArrayList<>(context.items() == null ? List.of() : context.items());
        sortedItems.sort(Comparator.comparing(OrderItemEntity::getId));

        if (awaitingPayment) {
            return sb.toString().trim();
        }

        String keysBlock = keyPreviewFormatter.renderFinalRequested(licenses, sortedItems);
        if (keysBlock == null || keysBlock.isBlank()) {
            keysBlock = "Keys: not available.";
        }

        if (artifactsSent) {
            sb.append("Keys: sent as artifact file.").append("\n");
        }

        sb.append(keysBlock).append("\n");
        return sb.toString().trim();
    }

    private void appendWhiteAdminOrderLink(StringBuilder sb, OrderEntity order, Locale locale) {
        if (order == null || order.getWhiteAdminId() == null) {
            return;
        }
        Long whiteAdminId = order.getWhiteAdminId();
        String url = buildWhiteAdminOrderUrl(whiteAdminId);
        String label = msg("telegram.preview.whiteadmin", locale);
        if (url == null || url.isBlank()) {
            sb.append(label).append(": ").append(whiteAdminId).append("\n");
            return;
        }
        sb.append(label).append(": ")
                .append("<a href=\"").append(url).append("\">").append(whiteAdminId).append("</a>")
                .append("\n");
    }

    private String buildWhiteAdminOrderUrl(Long whiteAdminId) {
        if (whiteAdminId == null || whiteAdminBaseUrl == null || whiteAdminOrderDetailsPage == null) {
            return null;
        }
        try {
            java.net.URI base = java.net.URI.create(whiteAdminBaseUrl);
            String path = whiteAdminOrderDetailsPage.startsWith("/")
                    ? whiteAdminOrderDetailsPage.substring(1)
                    : whiteAdminOrderDetailsPage;
            java.net.URI resolved = base.resolve(path);
            String separator = resolved.toString().contains("?") ? "&" : "?";
            return resolved + separator + "id=" + whiteAdminId;
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveArtifactReference(FinalNotificationContext context) {
        OrderEntity order = context.order();
        if (order == null) {
            return String.valueOf(context.orderId());
        }
        if (order.getWhiteAdminId() != null) {
            return String.valueOf(order.getWhiteAdminId());
        }
        if (order.getPortalId() != null) {
            return String.valueOf(order.getPortalId());
        }
        String comment = order.getUserComment();
        if (comment != null && !comment.isBlank()) {
            return sanitize(comment);
        }
        return String.valueOf(context.orderId());
    }

    private String sanitize(String value) {
        String cleaned = value.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (cleaned.isBlank()) {
            return "order";
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private String resolveProductName(LicenseEntity license) {
        Integer brandId = license.getBrandId();
        Integer productId = license.getProductId();
        if (brandId == null || productId == null) {
            return "";
        }
        return productRegistry.getProductById(productId)
                .filter(productInfo -> productInfo.brandId() == brandId)
                .map(productInfo -> productInfo.getName(Locale.ENGLISH, true, true))
                .orElseGet(() -> brandId + "/" + productId);
    }

    private String resolveItemProductName(OrderItemEntity item) {
        if (item == null || item.getProductId() == null || item.getProductBrandId() == null) {
            return "";
        }
        return productRegistry.getProductById(item.getProductId())
                .filter(productInfo -> productInfo.brandId() == item.getProductBrandId())
                .map(productInfo -> productInfo.getName(Locale.ENGLISH, true, true))
                .orElseGet(() -> item.getProductBrandId() + "/" + item.getProductId());
    }

    private String msg(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale == null ? Locale.ENGLISH : locale);
    }

    private void upsertControlMessage(Long chatId, FinalNotificationContext context, String text) {
        Integer controlMessageId = context.binding().getControlMessageId();
        if (controlMessageId == null) {
            try {
                Message sent = telegramSender.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text)
                        .parseMode("HTML")
                        .build());
                context.binding().setControlMessageId(sent.getMessageId());
            } catch (TelegramApiException e) {
                log.warn("Failed to send rich final telegram message chat={}: {}", chatId, e.getMessage());
            }
            return;
        }

        try {
            telegramSender.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(controlMessageId)
                    .text(text)
                    .parseMode("HTML")
                    .build());
            try {
                telegramSender.execute(EditMessageReplyMarkup.builder()
                        .chatId(chatId.toString())
                        .messageId(controlMessageId)
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException e) {
                if (e.getMessage() == null || !e.getMessage().contains("message is not modified")) {
                    throw e;
                }
                log.debug("Rich final keyboard already cleared chat={} message={}", chatId, controlMessageId);
            }
        } catch (TelegramApiException e) {
            log.warn("Failed to edit rich final telegram message chat={} message={}: {}",
                    chatId,
                    controlMessageId,
                    e.getMessage());
        }
    }
}
