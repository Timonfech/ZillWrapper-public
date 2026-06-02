package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.dino.DinoService;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderDeliveryTargetEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodNormalizer;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DinoLicenseGeneratorDescriptionTest {

    @Test
    void shouldUseUserCommentAsDescriptionAndInternalOrderIdAsName() {
        Fixtures fixtures = fixtures();
        OrderEntity order = order(42L, "client@example.com", "Client Name");
        order.setUserComment("ABC123");
        when(fixtures.orderRepository.findByIdWithDeliveryTargets(42L)).thenReturn(Optional.of(order));

        fixtures.generator.generate(item(), product());

        verify(fixtures.dinoService).createLicensePack(any(), eq(1), eq("42"), eq("client@example.com_ABC123"), eq(1), eq(365));
    }

    @Test
    void shouldFallbackToEmailAndClientNameDescription() {
        Fixtures fixtures = fixtures();
        when(fixtures.orderRepository.findByIdWithDeliveryTargets(42L))
                .thenReturn(Optional.of(order(42L, "client@example.com", "Client Name")));

        fixtures.generator.generate(item(), product());

        verify(fixtures.dinoService).createLicensePack(any(), eq(1), eq("42"), eq("client@example.com"), eq(1), eq(365));
    }

    @Test
    void shouldFallbackToEmailAndOrderIdDescription() {
        Fixtures fixtures = fixtures();
        when(fixtures.orderRepository.findByIdWithDeliveryTargets(42L))
                .thenReturn(Optional.of(order(42L, "client@example.com", null)));

        fixtures.generator.generate(item(), product());

        verify(fixtures.dinoService).createLicensePack(any(), eq(1), eq("42"), eq("client@example.com"), eq(1), eq(365));
    }

    private Fixtures fixtures() {
        DinoService dinoService = Mockito.mock(DinoService.class);
        LicenseRepository licenseRepository = Mockito.mock(LicenseRepository.class);
        ProductRegistry productRegistry = Mockito.mock(ProductRegistry.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        CryptoUtils cryptoUtils = Mockito.mock(CryptoUtils.class);

        when(productRegistry.resolveSourceType(1, 10)).thenReturn(Optional.of(SourceType.DINO_ADMIN));
        when(dinoService.createLicensePack(any(), eq(1), any(), any(), eq(1), eq(365)))
                .thenReturn(List.of(new LicenseEntity()));

        DinoLicenseGenerator generator = new DinoLicenseGenerator(
                dinoService,
                licenseRepository,
                productRegistry,
                orderItemRepository,
                orderRepository,
                cryptoUtils,
                new BusinessPeriodNormalizer()
        );
        return new Fixtures(generator, dinoService, orderRepository);
    }

    private OrderItemEntity item() {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(7L);
        item.setOrderId(42L);
        item.setCount(1);
        item.setPcPerLicense(1);
        item.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.YEAR));
        item.setProcessingStatus(ItemProcessingStatus.PENDING);
        return item;
    }

    private ProductInfo product() {
        return new ProductInfo(
                1,
                10,
                "DINO_GROUP",
                1,
                Pattern.compile(".*"),
                Map.of("en_short", "Dino Product"),
                Map.of(),
                List.of(KeyType.ONLINE)
        );
    }

    private OrderEntity order(Long id, String email, String clientName) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        if (clientName != null) {
            ClientEntity client = new ClientEntity();
            client.setName(clientName);
            order.setClient(client);
        }
        OrderDeliveryTargetEntity target = new OrderDeliveryTargetEntity();
        target.setOrder(order);
        target.setContactMethod(new EmailContact(email));
        order.getDeliveryTargets().add(target);
        return order;
    }

    private record Fixtures(
            DinoLicenseGenerator generator,
            DinoService dinoService,
            OrderRepository orderRepository
    ) {
    }
}
