package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.LicenseManagementService;
import org.apache.http.NameValuePair;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZabLicenseGeneratorParamsTest {

    @Test
    void shouldUseDefaultServerNumberWhenMissing() throws Exception {
        AbstractWhiteAdminClient client = Mockito.mock(AbstractWhiteAdminClient.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        LicenseManagementService licenseManagementService = Mockito.mock(LicenseManagementService.class);
        SourceManagementService sourceManagementService = Mockito.mock(SourceManagementService.class);
        LicenseRepository licenseRepository = Mockito.mock(LicenseRepository.class);
        EnrichmentTaskManager enrichmentTaskManager = Mockito.mock(EnrichmentTaskManager.class);
        ZabLicenseGenerator generator = new ZabLicenseGenerator(
                client,
                orderItemRepository,
                orderRepository,
                licenseManagementService,
                sourceManagementService,
                licenseRepository,
                enrichmentTaskManager,
                200,
                false,
                "latest.php",
                "admin_lic_generate_pack.php"
        );

        ProductInfo product = zabProduct();
        OrderItemEntity item = baseItem();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 95470L, null, null)));
        when(orderRepository.findByIdWithClient(10L)).thenReturn(Optional.of(orderWithClient(10L, 95470L, null, null)));

        Document packDoc = Jsoup.parse("<table><tbody><tr><td>ON123</td><td>OFF123</td></tr></tbody></table>");
        when(client.loadDocument(eq("admin_lic_generate_pack.php"), anyList(), anyBoolean())).thenReturn(packDoc);
        when(orderItemRepository.updateProcessingStatusById(1L, ItemProcessingStatus.GENERATED)).thenReturn(1);
        when(sourceManagementService.getOrCreateSource(any(), anyString())).thenReturn(new com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity(1L, com.zillya.timonfech.zillwrapper.core.source.SourceType.WHITE_ADMIN, "wa"));
        when(licenseManagementService.provisionWhiteAdminLicense(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity() {{ setId(1L); }});
        generator.generate(item, product);

        ArgumentCaptor<List<NameValuePair>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).loadDocument(eq("admin_lic_generate_pack.php"), captor.capture(), eq(true));
        assertEquals("1", findParam(captor.getValue(), "server_number"));
        assertEquals("95470", findParam(captor.getValue(), "cmt"));
        assertEquals("create_pack", findParam(captor.getValue(), "cmd"));
        assertEquals("12", findParam(captor.getValue(), "term"));
        assertEquals("1", findParam(captor.getValue(), "lcount"));
        assertEquals("1", findParam(captor.getValue(), "pack_size"));
        assertEquals("false", findParam(captor.getValue(), "excessblock"));
        assertEquals(null, findParam(captor.getValue(), "brand_id"));
        assertEquals(null, findParam(captor.getValue(), "product_id"));
        assertEquals(null, findParam(captor.getValue(), "order_item_id"));
        assertEquals(null, findParam(captor.getValue(), "period_days"));
        verify(orderItemRepository).updateProcessingStatusById(1L, ItemProcessingStatus.GENERATED);
    }

    @Test
    void shouldUseServerNumberFromTypedItemOptions() throws Exception {
        AbstractWhiteAdminClient client = Mockito.mock(AbstractWhiteAdminClient.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        LicenseManagementService licenseManagementService = Mockito.mock(LicenseManagementService.class);
        SourceManagementService sourceManagementService = Mockito.mock(SourceManagementService.class);
        LicenseRepository licenseRepository = Mockito.mock(LicenseRepository.class);
        EnrichmentTaskManager enrichmentTaskManager = Mockito.mock(EnrichmentTaskManager.class);
        ZabLicenseGenerator generator = new ZabLicenseGenerator(
                client,
                orderItemRepository,
                orderRepository,
                licenseManagementService,
                sourceManagementService,
                licenseRepository,
                enrichmentTaskManager,
                200,
                false,
                "latest.php",
                "admin_lic_generate_pack.php"
        );

        ProductInfo product = zabProduct();
        OrderItemEntity item = baseItem();
        item.setServerNumber(7);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, null, 123L, null)));
        when(orderRepository.findByIdWithClient(10L)).thenReturn(Optional.of(orderWithClient(10L, null, 123L, null)));

        Document packDoc = Jsoup.parse("<table><tbody><tr><td>ON123</td><td>OFF123</td></tr></tbody></table>");
        when(client.loadDocument(eq("admin_lic_generate_pack.php"), anyList(), anyBoolean())).thenReturn(packDoc);
        when(orderItemRepository.updateProcessingStatusById(1L, ItemProcessingStatus.GENERATED)).thenReturn(1);
        when(sourceManagementService.getOrCreateSource(any(), anyString())).thenReturn(new com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity(1L, com.zillya.timonfech.zillwrapper.core.source.SourceType.WHITE_ADMIN, "wa"));
        when(licenseManagementService.provisionWhiteAdminLicense(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity() {{ setId(1L); }});
        generator.generate(item, product);

        ArgumentCaptor<List<NameValuePair>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).loadDocument(eq("admin_lic_generate_pack.php"), captor.capture(), eq(true));
        assertEquals("7", findParam(captor.getValue(), "server_number"));
        assertEquals("123", findParam(captor.getValue(), "cmt"));
        verify(orderItemRepository).updateProcessingStatusById(1L, ItemProcessingStatus.GENERATED);
    }

    private ProductInfo zabProduct() {
        return new ProductInfo(
                4,
                2,
                null,
                1,
                Pattern.compile(".*"),
                Map.of("en_short", "ZAB"),
                Map.of(),
                List.of(KeyType.ONLINE)
        );
    }

    private OrderItemEntity baseItem() {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(1L);
        item.setOrderId(10L);
        item.setCount(1);
        item.setPcPerLicense(1);
        item.setBusinessPeriod(new BusinessPeriod(1, BusinessPeriodUnit.YEAR));
        item.setProcessingStatus(ItemProcessingStatus.PENDING);
        return item;
    }

    private OrderEntity order(Long id, Long whiteAdminId, Long portalId, String userComment) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setWhiteAdminId(whiteAdminId);
        order.setPortalId(portalId);
        order.setUserComment(userComment);
        return order;
    }

    private OrderEntity orderWithClient(Long id, Long whiteAdminId, Long portalId, String userComment) {
        OrderEntity order = order(id, whiteAdminId, portalId, userComment);
        com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity client =
                new com.zillya.timonfech.zillwrapper.core.entities.user_clients.ClientEntity();
        client.setId(1L);
        order.setClient(client);
        return order;
    }

    private String findParam(List<NameValuePair> params, String name) {
        return params.stream()
                .filter(p -> name.equals(p.getName()))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }
}
