package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.routing.DeliveryTargetSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.routing.OrderOperationContext;
import com.zillya.timonfech.zillwrapper.core.services.OrderProcessingService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.LicenseManagementService;
import com.zillya.timonfech.zillwrapper.core.transport.ZillyaTelegramBot;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "users.sync.enabled=false",
        "telegram.bot.zill.bot-token=test-token",
        "telegram.bot.zill.bot-name=test-bot",
        "support.chat.url=https://example.test/support",
        "management.health.mail.enabled=false"
})
@Transactional
class EmailNotifyFlowIntegrationTest {

    @Autowired
    private OrderProcessingService orderProcessingService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRegistry productRegistry;
    @Autowired
    private LicenseManagementService licenseManagementService;
    @Autowired
    private EmailNotifyService emailNotifyService;

    @MockitoBean
    private JavaMailSender javaMailSender;
    @MockitoBean
    private ZillyaTelegramBot zillyaTelegramBot;

    @BeforeEach
    void setUp() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldRenderRealEmailTemplateAndRespectOfflineKeyAndMonthPeriod() throws Exception {
        ProductInfo product = resolveTestProduct();
        OrderOperationContext ctx = new OrderOperationContext(
                1L,
                null,
                "test@example.com",
                List.of(new OrderItemSpec(
                        product,
                        1,
                        new BusinessPeriod(1, BusinessPeriodUnit.MONTH),
                        1,
                        List.of(OutputType.TEXT),
                        List.of(KeyType.OFFLINE),
                        false,
                        OrderItemOptions.empty()
                )),
                List.of(new DeliveryTargetSpec(ContactMethodType.EMAIL, "test@example.com", OutputType.TEXT)),
                null
        );
        ctx.setLocaleTag("en");

        Long orderId = orderProcessingService.createOrder(ctx);
        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElseThrow();
        assertFalse(order.getItems().isEmpty());
        OrderItemEntity item = order.getItems().getFirst();

        // Mock generation output only: online short, offline long.
        WhiteAdminKeyEntity generatedKey = new WhiteAdminKeyEntity();
        generatedKey.setOnlineKey("ON-1");
        generatedKey.setOfflineKey("OFFLINE-VERY-LONG-KEY-1234567890");
        licenseManagementService.provisionWhiteAdminLicense(
                order.getId(),
                item.getId(),
                order.getClient() == null ? null : order.getClient().getId(),
                1L,
                item.getBusinessPeriod(),
                item.getProductId(),
                item.getProductBrandId(),
                generatedKey
        );
        item.setProcessingStatus(ItemProcessingStatus.GENERATED);

        EmailSendResult sendResult = emailNotifyService.sendOrderItems(
                order,
                List.of(item),
                List.of(),
                java.util.Map.of(),
                OperationType.LICENSE_FULFILLMENT,
                Locale.ENGLISH
        );

        assertTrue(sendResult.getDeliveredItemIds().contains(item.getId()));
        assertTrue(sendResult.getFailedItemIds().isEmpty());

        ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(mimeCaptor.capture());
        MimeMessage sent = mimeCaptor.getValue();
        assertNotNull(sent);
        assertEquals("License for Zillya! Total Security 3.0 (OFFLINE)", sent.getSubject());

        Object content = sent.getContent();
        String html = extractTextContent(content);
        String normalized = new String(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertTrue(normalized.contains("OFFLINE-VERY-LONG-KEY-1234567890"));
        assertFalse(normalized.contains("ON-1"));
    }

    @Test
    void shouldRenderOnlineOnlyForResendFlowWhenRequestedOnline() throws Exception {
        ProductInfo product = resolveTestProduct();
        OrderOperationContext ctx = new OrderOperationContext(
                1L,
                null,
                "test@example.com",
                List.of(new OrderItemSpec(
                        product,
                        1,
                        new BusinessPeriod(1, BusinessPeriodUnit.MONTH),
                        1,
                        List.of(OutputType.TEXT),
                        List.of(KeyType.ONLINE),
                        false,
                        OrderItemOptions.empty()
                )),
                List.of(new DeliveryTargetSpec(ContactMethodType.EMAIL, "test@example.com", OutputType.TEXT)),
                null
        );
        ctx.setLocaleTag("en");

        Long orderId = orderProcessingService.createOrder(ctx);
        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElseThrow();
        OrderItemEntity item = order.getItems().getFirst();

        WhiteAdminKeyEntity generatedKey = new WhiteAdminKeyEntity();
        generatedKey.setOnlineKey("ON-RESEND-1");
        generatedKey.setOfflineKey("OFF-RESEND-1");
        licenseManagementService.provisionWhiteAdminLicense(
                order.getId(),
                item.getId(),
                order.getClient() == null ? null : order.getClient().getId(),
                1L,
                item.getBusinessPeriod(),
                item.getProductId(),
                item.getProductBrandId(),
                generatedKey
        );
        item.setProcessingStatus(ItemProcessingStatus.GENERATED);

        EmailSendResult sendResult = emailNotifyService.sendOrderItems(
                order,
                List.of(item),
                List.of(),
                java.util.Map.of(),
                OperationType.RESEND_NOTIFICATION,
                Locale.ENGLISH
        );

        assertTrue(sendResult.getDeliveredItemIds().contains(item.getId()));
        assertTrue(sendResult.getFailedItemIds().isEmpty());

        ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(mimeCaptor.capture());
        MimeMessage sent = mimeCaptor.getValue();
        assertNotNull(sent);
        assertEquals("License for Zillya! Total Security 3.0", sent.getSubject());
        String html = extractTextContent(sent.getContent());
        assertTrue(html.contains("ON-RESEND-1"));
        assertFalse(html.contains("OFF-RESEND-1"));
    }

    @Test
    void shouldFallbackToTextWhenExcelRequestedButAttachmentMissingAndTextAllowed() throws Exception {
        ProductInfo product = resolveTestProduct();
        OrderOperationContext ctx = new OrderOperationContext(
                1L,
                null,
                "test@example.com",
                List.of(new OrderItemSpec(
                        product,
                        1,
                        new BusinessPeriod(1, BusinessPeriodUnit.MONTH),
                        1,
                        List.of(OutputType.EXCEL, OutputType.TEXT),
                        List.of(KeyType.ONLINE),
                        false,
                        OrderItemOptions.empty()
                )),
                List.of(new DeliveryTargetSpec(ContactMethodType.EMAIL, "test@example.com", OutputType.EXCEL)),
                null
        );
        ctx.setLocaleTag("en");

        Long orderId = orderProcessingService.createOrder(ctx);
        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElseThrow();
        OrderItemEntity item = order.getItems().getFirst();

        WhiteAdminKeyEntity generatedKey = new WhiteAdminKeyEntity();
        generatedKey.setOnlineKey("EXCEL-FALLBACK-ON");
        generatedKey.setOfflineKey("EXCEL-FALLBACK-OFF");
        licenseManagementService.provisionWhiteAdminLicense(
                order.getId(),
                item.getId(),
                order.getClient() == null ? null : order.getClient().getId(),
                1L,
                item.getBusinessPeriod(),
                item.getProductId(),
                item.getProductBrandId(),
                generatedKey
        );
        item.setProcessingStatus(ItemProcessingStatus.GENERATED);

        EmailSendResult sendResult = emailNotifyService.sendOrderItems(
                order,
                List.of(item),
                List.of(),
                java.util.Map.of(),
                OperationType.LICENSE_FULFILLMENT,
                Locale.ENGLISH
        );

        assertTrue(sendResult.getDeliveredItemIds().contains(item.getId()));
        assertTrue(sendResult.getFailedItemIds().isEmpty());
        verify(javaMailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void shouldFailWhenExcelRequestedButAttachmentMissingAndTextNotAllowed() {
        ProductInfo product = resolveTestProduct();
        OrderOperationContext ctx = new OrderOperationContext(
                1L,
                null,
                "test@example.com",
                List.of(new OrderItemSpec(
                        product,
                        1,
                        new BusinessPeriod(1, BusinessPeriodUnit.MONTH),
                        1,
                        List.of(OutputType.EXCEL),
                        List.of(KeyType.ONLINE),
                        false,
                        OrderItemOptions.empty()
                )),
                List.of(new DeliveryTargetSpec(ContactMethodType.EMAIL, "test@example.com", OutputType.EXCEL)),
                null
        );
        ctx.setLocaleTag("en");

        Long orderId = orderProcessingService.createOrder(ctx);
        OrderEntity order = orderRepository.findByIdWithDeliveryTargets(orderId).orElseThrow();
        OrderItemEntity item = order.getItems().getFirst();
        item.setProcessingStatus(ItemProcessingStatus.GENERATED);

        EmailSendResult sendResult = emailNotifyService.sendOrderItems(
                order,
                List.of(item),
                List.of(),
                java.util.Map.of(),
                OperationType.LICENSE_FULFILLMENT,
                Locale.ENGLISH
        );

        assertTrue(sendResult.getDeliveredItemIds().isEmpty());
        assertTrue(sendResult.getFailedItemIds().contains(item.getId()));
        assertTrue(sendResult.getErrors().stream().anyMatch(e -> e.contains("Excel attachment missing")));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    private ProductInfo resolveTestProduct() {
        Optional<ProductInfo> byId = productRegistry.getProductById(5);
        if (byId.isPresent()) {
            return byId.get();
        }
        return productRegistry.getAllProducts().stream().findFirst().orElseThrow();
    }

    private String extractTextContent(Object content) throws Exception {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof MimeMultipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                Object part = mp.getBodyPart(i).getContent();
                sb.append(extractTextContent(part));
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }
}
