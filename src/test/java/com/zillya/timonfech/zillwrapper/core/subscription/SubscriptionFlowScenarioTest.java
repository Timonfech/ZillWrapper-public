package com.zillya.timonfech.zillwrapper.core.subscription;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.enrichment.EnrichmentSchedulerSettingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.BaseKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.TelegramOperationBindingEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OutputType;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.subscription.LicenseSubscriptionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientType;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.*;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemOptions;
import com.zillya.timonfech.zillwrapper.core.routing.OrderItemSpec;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.core.subscription.events.SubscriptionDetailedDeltaEvent;
import com.zillya.timonfech.zillwrapper.core.subscription.notifications.SubscriptionNotificationRouter;
import com.zillya.timonfech.zillwrapper.core.subscription.notifications.TelegramSubscriptionNotificationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionFlowScenarioTest {

    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private LicenseRepository licenseRepository;
    @Mock
    private LicenseSubscriptionRepository subscriptionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EnrichmentSchedulerSettingRepository schedulerSettingRepository;
    @Mock
    private EnrichmentTaskManager enrichmentTaskManager;
    @Mock
    private SubscriptionWarningDeliveryRepository warningDeliveryRepository;
    @Mock
    private SourceRepository sourceRepository;
    @Mock
    private OperationExecutionRepository operationExecutionRepository;
    @Mock
    private TelegramOperationBindingRepository bindingRepository;
    @Mock
    private AbsSender telegramSender;

    private LicenseSubscriptionService subscriptionService;
    private SubscriptionEntityUpdateListener updateListener;
    private LicenseSubscriptionSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        subscriptionService = new LicenseSubscriptionService(
                orderItemRepository,
                orderRepository,
                licenseRepository,
                subscriptionRepository,
                new WarningLeadParser()
        );
        updateListener = new SubscriptionEntityUpdateListener(subscriptionRepository);
        schedulerService = new LicenseSubscriptionSchedulerService(
                schedulerSettingRepository,
                subscriptionRepository,
                licenseRepository,
                orderItemRepository,
                enrichmentTaskManager,
                warningDeliveryRepository
        );
    }

    @Test
    void shouldSetupSubscriptionsAndRespectPartnerDefaultOptOut() {
        long orderId = 10L;
        OrderItemEntity offlineItem = new OrderItemEntity();
        offlineItem.setId(100L);
        offlineItem.setOrderId(orderId);
        offlineItem.setKeyTypes(List.of(KeyType.OFFLINE));
        offlineItem.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.YEAR));

        OrderItemEntity onlineItem = new OrderItemEntity();
        onlineItem.setId(101L);
        onlineItem.setOrderId(orderId);
        onlineItem.setKeyTypes(List.of(KeyType.ONLINE));
        onlineItem.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.YEAR));

        LicenseEntity offlineLicense = new LicenseEntity();
        offlineLicense.setId(500L);
        offlineLicense.setOrderId(orderId);
        offlineLicense.setOrderItemId(100L);
        offlineLicense.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        LicenseEntity onlineLicense = new LicenseEntity();
        onlineLicense.setId(501L);
        onlineLicense.setOrderId(orderId);
        onlineLicense.setOrderItemId(101L);
        onlineLicense.setExpiresAt(Instant.parse("2027-01-01T00:00:00Z"));

        ClientEntity standardClient = new ClientEntity();
        standardClient.setClientType(ClientType.STANDARD);
        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity standardOrder = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        standardOrder.setId(orderId);
        standardOrder.setClient(standardClient);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(standardOrder));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(orderId)).thenReturn(List.of(offlineItem, onlineItem));
        when(licenseRepository.findByOrderItemId(100L)).thenReturn(List.of(offlineLicense));
        when(licenseRepository.findByOrderItemId(101L)).thenReturn(List.of(onlineLicense));
        when(subscriptionRepository.findByLicenseId(500L)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByLicenseId(501L)).thenReturn(Optional.empty());

        OrderItemSpec firstSpec = new OrderItemSpec(
                product(4, 2),
                1,
                new BusinessPeriod(1, BusinessPeriodUnit.YEAR),
                1,
                List.of(OutputType.TEXT),
                List.of(KeyType.OFFLINE),
                true,
                new OrderItemOptions(1, true, true, "2d", 90, null)
        );
        OrderItemSpec secondSpec = new OrderItemSpec(
                product(5, 2),
                1,
                new BusinessPeriod(1, BusinessPeriodUnit.YEAR),
                1,
                List.of(OutputType.TEXT),
                List.of(KeyType.ONLINE),
                true,
                new OrderItemOptions(null, false, false, null, null, null)
        );

        subscriptionService.setupSubscriptionsForOrder(orderId, 1L, 77L, List.of(firstSpec, secondSpec));

        ArgumentCaptor<LicenseSubscriptionEntity> subCaptor = ArgumentCaptor.forClass(LicenseSubscriptionEntity.class);
        verify(subscriptionRepository, atLeastOnce()).save(subCaptor.capture());
        List<LicenseSubscriptionEntity> savedSubs = subCaptor.getAllValues().stream()
                .filter(s -> s.getLicenseId() != null)
                .toList();
        assertEquals(2, savedSubs.size());

        LicenseSubscriptionEntity offlineSub = savedSubs.stream().filter(s -> s.getLicenseId().equals(500L)).findFirst().orElseThrow();
        assertEquals(SubscriptionLeadUnit.DAY, offlineSub.getWarningLeadUnit());
        assertEquals(2, offlineSub.getWarningLeadAmount());
        assertEquals(90, offlineSub.getCheckIntervalMinutes());
        assertNotNull(offlineSub.getExpectedExpiration());

        LicenseSubscriptionEntity onlineSub = savedSubs.stream().filter(s -> s.getLicenseId().equals(501L)).findFirst().orElseThrow();
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), onlineSub.getExpectedExpiration());

        ClientEntity partnerClient = new ClientEntity();
        partnerClient.setClientType(ClientType.PARTNER);
        com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity partnerOrder = new com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity();
        partnerOrder.setId(orderId);
        partnerOrder.setClient(partnerClient);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(partnerOrder));

        OrderItemSpec partnerDefaultSpec = new OrderItemSpec(
                product(4, 2),
                1,
                new BusinessPeriod(1, BusinessPeriodUnit.YEAR),
                1,
                List.of(OutputType.TEXT),
                List.of(KeyType.OFFLINE),
                true,
                new OrderItemOptions(1, true, true, "2d", 90, false)
        );
        clearInvocations(subscriptionRepository, licenseRepository);
        subscriptionService.setupSubscriptionsForOrder(orderId, 1L, 77L, List.of(partnerDefaultSpec, secondSpec));
        verify(licenseRepository, never()).findByOrderItemId(any());
        verify(subscriptionRepository, never()).save(any(LicenseSubscriptionEntity.class));
    }

    @Test
    void shouldMarkWarningOnSchedulerTick() {
        EnrichmentSchedulerSettingEntity settings = new EnrichmentSchedulerSettingEntity();
        settings.setJobName(LicenseSubscriptionSchedulerService.JOB_NAME);
        settings.setEnabled(true);
        settings.setDelayMinutes(720);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy("test");
        when(schedulerSettingRepository.findById(LicenseSubscriptionSchedulerService.JOB_NAME)).thenReturn(Optional.of(settings));

        LicenseSubscriptionEntity sub = new LicenseSubscriptionEntity();
        sub.setLicenseId(900L);
        sub.setStatus(LicenseSubscriptionEntity.SubscriptionStatus.ACTIVE);
        sub.setWarningLeadAmount(3);
        sub.setWarningLeadUnit(SubscriptionLeadUnit.DAY);
        sub.setExpectedExpiration(Instant.now().plus(1, ChronoUnit.DAYS));
        sub.setNextCheckAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(subscriptionRepository.findByStatusInAndNextCheckAtBefore(any(), any())).thenReturn(List.of(sub));

        schedulerService.tick();

        ArgumentCaptor<LicenseSubscriptionEntity> captor = ArgumentCaptor.forClass(LicenseSubscriptionEntity.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(LicenseSubscriptionEntity.SubscriptionStatus.ACTIVE, captor.getValue().getStatus());
        assertNull(captor.getValue().getNotifiedAt());
        verify(warningDeliveryRepository).save(any());
    }

    @Test
    void shouldRouteDetailedUpdateToTelegramAsReplyToControlMessage() throws Exception {
        verify(eventPublisher, never()).publishEvent(any());

        SourceEntity telegramSource = new SourceEntity(1L, SourceType.TELEGRAM, "tg");
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(telegramSource));

        OperationExecutionEntity orderCreation = new OperationExecutionEntity();
        orderCreation.setId(BigInteger.valueOf(300));
        orderCreation.setParentId(BigInteger.valueOf(200));
        orderCreation.setOperationType(OperationType.ORDER_CREATION);
        when(operationExecutionRepository.findByEntityIdAndOperationTypeOrderByCreatedAtDesc(77L, OperationType.ORDER_CREATION))
                .thenReturn(List.of(orderCreation));

        TelegramOperationBindingEntity binding = new TelegramOperationBindingEntity();
        binding.setOperationId(BigInteger.valueOf(200));
        binding.setChatId(5509504162L);
        binding.setControlMessageId(563);
        when(bindingRepository.findByOperationId(BigInteger.valueOf(200))).thenReturn(Optional.of(binding));

        SubscriptionDetailedDeltaEvent delta = new SubscriptionDetailedDeltaEvent(
                this,
                1L,
                77L,
                500L,
                "ABCDEFGH",
                Instant.now(),
                List.of()
        );

        TelegramSubscriptionNotificationHandler tgHandler = new TelegramSubscriptionNotificationHandler(
                sourceRepository,
                operationExecutionRepository,
                bindingRepository,
                telegramSender
        );
        SubscriptionNotificationRouter router = new SubscriptionNotificationRouter(List.of(tgHandler));
        router.onDetailedDelta(delta);

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramSender).execute(messageCaptor.capture());
        SendMessage sent = messageCaptor.getValue();
        assertEquals("5509504162", sent.getChatId());
        assertEquals(563, sent.getReplyToMessageId());
        assertTrue(sent.getText().contains("ABCDEFGH"));
    }

    @Test
    void shouldSkipDetailedTelegramNotifyWhenSourceOrBindingMissing() throws Exception {
        when(sourceRepository.findById(1L)).thenReturn(Optional.empty());
        SubscriptionDetailedDeltaEvent delta = new SubscriptionDetailedDeltaEvent(
                this,
                1L,
                77L,
                500L,
                "ABCDEFGH",
                Instant.now(),
                List.of()
        );
        TelegramSubscriptionNotificationHandler tgHandler = new TelegramSubscriptionNotificationHandler(
                sourceRepository,
                operationExecutionRepository,
                bindingRepository,
                telegramSender
        );
        SubscriptionNotificationRouter router = new SubscriptionNotificationRouter(List.of(tgHandler));
        router.onDetailedDelta(delta);
        verifyNoInteractions(telegramSender);
    }

    private static com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo product(int productId, int brandId) {
        return new com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo(
                productId,
                brandId,
                "grp",
                1,
                Pattern.compile(".*", Pattern.CASE_INSENSITIVE),
                Map.of("en_full", "Product"),
                Map.of(),
                List.of(KeyType.ONLINE, KeyType.OFFLINE)
        );
    }
}
