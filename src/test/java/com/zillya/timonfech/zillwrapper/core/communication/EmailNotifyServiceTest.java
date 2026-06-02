package com.zillya.timonfech.zillwrapper.core.communication;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.license.BaseKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailNotifyServiceTest {

    @Test
    void shouldUseProductionPeriodAndKeySelectionLogicWithOnlyGeneratedKeysMocked() {
        EmailCommunicationService emailCommunicationService = mock(EmailCommunicationService.class);
        ProductRegistry productRegistry = mock(ProductRegistry.class);
        LicenseRepository licenseRepository = mock(LicenseRepository.class);
        CryptoUtils cryptoUtils = mock(CryptoUtils.class);
        EmailTemplateRoutingService routingService = mock(EmailTemplateRoutingService.class);
        EmailNotifyService service = new EmailNotifyService(
                emailCommunicationService,
                routingService,
                productRegistry,
                licenseRepository,
                cryptoUtils
        );

        OrderEntity order = new OrderEntity();
        order.setId(10L);

        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setEnabled(true);
        target.setContactMethod(new EmailContact("test@example.com"));
        order.setDeliveryTargets(List.of(target));

        OrderItemEntity item = new OrderItemEntity();
        item.setId(101L);
        item.setProductId(5);
        item.setPcPerLicense(1);
        item.setCount(1);
        item.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.MONTH));
        item.setOutputTypes(List.of(OutputType.TEXT));
        item.setKeyTypes(List.of(KeyType.OFFLINE));

        ProductInfo productInfo = new ProductInfo(
                5, 2, null, 1, Pattern.compile(".*"),
                Map.of("en_full", "Zillya! Total Security"),
                Map.of("offline_direct_link", "https://example.com/offline"),
                List.of(KeyType.ONLINE, KeyType.OFFLINE)
        );
        when(productRegistry.getProductById(5)).thenReturn(Optional.of(productInfo));
        when(routingService.resolve(eq(OperationType.NOTIFY), any(), any()))
                .thenReturn(new ResolvedEmailTemplate(
                        "test",
                        "license_email",
                        "email.license.subject.single",
                        "email.license.subject.plural",
                        "email.license.subject.suffix.offline"
                ));

        // Mock only generation output shape: online shorter, offline longer.
        BaseKeyEntity key = new BaseKeyEntity();
        key.setOnlineKey("SHORT-ON");
        key.setOfflineKey("VERY-LONG-OFFLINE-KEY-1234567890");
        LicenseEntity generatedLicense = new LicenseEntity();
        generatedLicense.setKey(key);
        when(licenseRepository.findByOrderItemId(101L)).thenReturn(List.of(generatedLicense));

        EmailSendResult result = service.sendOrderItems(order, List.of(item), List.of(), Map.of(), OperationType.NOTIFY, Locale.ENGLISH);
        assertTrue(result.getDeliveredItemIds().contains(101L));
        assertTrue(result.getFailedItemIds().isEmpty());

        ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailCommunicationService).send(eq("test@example.com"), any(ResolvedEmailTemplate.class), modelCaptor.capture(), eq(Locale.ENGLISH), anyList());
        Map<String, Object> model = modelCaptor.getValue();

        assertEquals("1 month", model.get("durationText"));
        assertEquals("OFFLINE", model.get("keyType"));

        List<String> keys = (List<String>) model.get("keys");
        assertNotNull(keys);
        assertEquals(1, keys.size());
        assertEquals("VERY-LONG-OFFLINE-KEY-1234567890", keys.getFirst());
    }

    @Test
    void shouldFailItemWhenExcelRequestedWithoutAttachmentAndTextFallbackUnavailable() {
        EmailCommunicationService emailCommunicationService = mock(EmailCommunicationService.class);
        ProductRegistry productRegistry = mock(ProductRegistry.class);
        LicenseRepository licenseRepository = mock(LicenseRepository.class);
        CryptoUtils cryptoUtils = mock(CryptoUtils.class);
        EmailTemplateRoutingService routingService = mock(EmailTemplateRoutingService.class);
        EmailNotifyService service = new EmailNotifyService(
                emailCommunicationService,
                routingService,
                productRegistry,
                licenseRepository,
                cryptoUtils
        );

        OrderEntity order = new OrderEntity();
        order.setId(10L);
        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setEnabled(true);
        target.setContactMethod(new EmailContact("test@example.com"));
        order.setDeliveryTargets(List.of(target));

        OrderItemEntity item = new OrderItemEntity();
        item.setId(101L);
        item.setProductId(5);
        item.setOutputTypes(List.of(OutputType.EXCEL));
        item.setKeyTypes(List.of(KeyType.ONLINE));

        ProductInfo productInfo = new ProductInfo(
                5, 2, null, 1, Pattern.compile(".*"),
                Map.of("en_full", "Zillya! Total Security"),
                Map.of(),
                List.of(KeyType.ONLINE, KeyType.OFFLINE)
        );
        when(productRegistry.getProductById(5)).thenReturn(Optional.of(productInfo));
        when(licenseRepository.findByOrderItemId(101L)).thenReturn(List.of());

        EmailSendResult result = service.sendOrderItems(order, List.of(item), List.of(), Map.of(), OperationType.NOTIFY, Locale.ENGLISH);
        assertTrue(result.getDeliveredItemIds().isEmpty());
        assertTrue(result.getFailedItemIds().contains(101L));
        verify(emailCommunicationService, never()).send(anyString(), any(ResolvedEmailTemplate.class), anyMap(), any(), anyList());
    }

    @Test
    void shouldFallbackToTextWhenExcelMissingAndTextAllowed() {
        EmailCommunicationService emailCommunicationService = mock(EmailCommunicationService.class);
        ProductRegistry productRegistry = mock(ProductRegistry.class);
        LicenseRepository licenseRepository = mock(LicenseRepository.class);
        CryptoUtils cryptoUtils = mock(CryptoUtils.class);
        EmailTemplateRoutingService routingService = mock(EmailTemplateRoutingService.class);
        EmailNotifyService service = new EmailNotifyService(
                emailCommunicationService,
                routingService,
                productRegistry,
                licenseRepository,
                cryptoUtils
        );

        OrderEntity order = new OrderEntity();
        order.setId(20L);
        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setEnabled(true);
        target.setContactMethod(new EmailContact("test@example.com"));
        target.setOutputFormat(OutputType.EXCEL);
        order.setDeliveryTargets(List.of(target));

        OrderItemEntity item = new OrderItemEntity();
        item.setId(202L);
        item.setProductId(5);
        item.setOutputTypes(List.of(OutputType.EXCEL, OutputType.TEXT));
        item.setKeyTypes(List.of(KeyType.ONLINE));

        ProductInfo productInfo = new ProductInfo(
                5, 2, null, 1, Pattern.compile(".*"),
                Map.of("en_full", "Zillya! Total Security"),
                Map.of(),
                List.of(KeyType.ONLINE, KeyType.OFFLINE)
        );
        when(productRegistry.getProductById(5)).thenReturn(Optional.of(productInfo));
        when(routingService.resolve(eq(OperationType.NOTIFY), any(), any()))
                .thenReturn(new ResolvedEmailTemplate(
                        "excel",
                        "license_email",
                        "email.license.subject.single",
                        "email.license.subject.plural",
                        "email.license.subject.suffix.offline"
                ));

        BaseKeyEntity key = new BaseKeyEntity();
        key.setOnlineKey("ONLINE-K");
        LicenseEntity license = new LicenseEntity();
        license.setKey(key);
        when(licenseRepository.findByOrderItemId(202L)).thenReturn(List.of(license));

        EmailSendResult result = service.sendOrderItems(order, List.of(item), List.of(), Map.of(), OperationType.NOTIFY, Locale.ENGLISH);

        assertTrue(result.getDeliveredItemIds().contains(202L));
        assertTrue(result.getFailedItemIds().isEmpty());
        verify(emailCommunicationService).send(eq("test@example.com"), any(ResolvedEmailTemplate.class), anyMap(), eq(Locale.ENGLISH), eq(List.of()));
    }
}
