package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.AbstractWhiteAdminClient;
import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentTaskManager;
import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.WhiteAdminKeyEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderItemRepository;
import com.zillya.timonfech.zillwrapper.core.repos.OrderRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.services.persistance.LicenseManagementService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class AbstractWhiteAdminPackLicenseGenerator implements LicenseGenerator {
    private static final ConcurrentHashMap<String, AtomicBoolean> CATCHUP_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> CATCHUP_PENDING = new ConcurrentHashMap<>();

    private final AbstractWhiteAdminClient client;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final LicenseManagementService licenseManagementService;
    private final SourceManagementService sourceManagementService;
    private final LicenseRepository licenseRepository;
    private final EnrichmentTaskManager enrichmentTaskManager;
    private final int catchupBootstrapWindow;
    private final boolean catchupEnabled;

    protected AbstractWhiteAdminPackLicenseGenerator(
            AbstractWhiteAdminClient client,
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository,
            LicenseManagementService licenseManagementService,
            SourceManagementService sourceManagementService,
            LicenseRepository licenseRepository,
            EnrichmentTaskManager enrichmentTaskManager,
            @Value("${whiteAdminPanel.catchupBootstrapWindow:200}") int catchupBootstrapWindow,
            @Value("${whiteAdminPanel.catchupEnabled:true}") boolean catchupEnabled
    ) {
        this.client = client;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.licenseManagementService = licenseManagementService;
        this.sourceManagementService = sourceManagementService;
        this.licenseRepository = licenseRepository;
        this.enrichmentTaskManager = enrichmentTaskManager;
        this.catchupBootstrapWindow = catchupBootstrapWindow;
        this.catchupEnabled = catchupEnabled;
    }

    @Override
    public Optional<SourceType> sourceType() {
        return Optional.of(SourceType.WHITE_ADMIN);
    }

    @Override
    @Transactional
    public void generate(OrderItemEntity item, ProductInfo product) {
        generate(item, product, null);
    }

    @Override
    @Transactional
    public void generate(OrderItemEntity item, ProductInfo product, Long originSourceId) {
        try {
            List<NameValuePair> params = buildBaseParams(item, product);
            params = extendParams(params, item, product);
            Document doc = client.loadDocument(getEndpoint(product), params, true);
            List<GeneratedPackKey> parsedKeys = parsePack(doc);
            if (parsedKeys.isEmpty()) {
                log.warn("WhiteAdmin pack returned no keys on first attempt for item {}. Trying relogin + retry.", item.getId());
                client.relogin();
                doc = client.loadDocument(getEndpoint(product), params, true);
                parsedKeys = parsePack(doc);
                if (parsedKeys.isEmpty()) {
                    throw new RuntimeException("WhiteAdmin pack returned no keys");
                }
            }

            Long providerSourceId = sourceManagementService.getOrCreateSource(SourceType.WHITE_ADMIN, "whiteadmin").getId();
            OrderEntity order = resolveOrder(item);
            if (order == null) {
                throw new RuntimeException("Order not found for item " + item.getId());
            }
            Long effectiveOriginSourceId = originSourceId != null ? originSourceId : providerSourceId;
            if (order.getClient() == null || order.getClient().getId() == null) {
                throw new RuntimeException("Order client is missing for item " + item.getId());
            }
            for (GeneratedPackKey parsedKey : parsedKeys) {
                WhiteAdminKeyEntity key = new WhiteAdminKeyEntity();
                key.setOnlineKey(parsedKey.onlineKey());
                key.setOfflineKey(parsedKey.offlineKey());
                LicenseEntity saved = licenseManagementService.provisionWhiteAdminLicense(
                        order.getId(),
                        item.getId(),
                        order.getClient().getId(),
                        effectiveOriginSourceId,
                        item.getBusinessPeriod(),
                        product.productId(),
                        product.brandId(),
                        key
                );
                if (saved.getId() == null) {
                    throw new RuntimeException("Failed to persist WhiteAdmin license for item " + item.getId());
                }
            }

            updateItemStatus(item.getId(), ItemProcessingStatus.GENERATED);
            triggerPostGenerationCatchupEnrichment(product, providerSourceId);
            log.info("WhiteAdmin license pack generated for item {} product {}/{}",
                    item.getId(),
                    product.brandId(),
                    product.productId());
        } catch (Exception ex) {
            log.error("WhiteAdmin generator failed for item {} product {}/{}: {}",
                    item.getId(),
                    product.brandId(),
                    product.productId(),
                    ex.getMessage());
            updateItemStatus(item.getId(), ItemProcessingStatus.FAILED);
            throw new RuntimeException(ex);
        }
    }

    private void updateItemStatus(Long itemId, ItemProcessingStatus status) {
        int updated = orderItemRepository.updateProcessingStatusById(itemId, status);
        if (updated == 0) {
            log.warn("Order item {} was not updated to {} (row is missing or concurrently removed)", itemId, status);
        }
    }

    protected List<NameValuePair> buildBaseParams(OrderItemEntity item, ProductInfo product) {
        validateItem(item);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("cmd", "create_pack"));
        params.add(new BasicNameValuePair("term", String.valueOf(toWhiteAdminMonths(item.getBusinessPeriod()))));
        params.add(new BasicNameValuePair("lcount", String.valueOf(item.getPcPerLicense())));
        params.add(new BasicNameValuePair("pack_size", String.valueOf(item.getCount())));
        params.add(new BasicNameValuePair("cmt", resolveComment(item)));
        params.add(new BasicNameValuePair("excessblock", "false"));
        return params;
    }

    protected void validateItem(OrderItemEntity item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null");
        }
        if (item.getBusinessPeriod() == null) {
            throw new IllegalArgumentException("Item period must not be null");
        }
        if (item.getPcPerLicense() == null || item.getPcPerLicense() <= 0) {
            throw new IllegalArgumentException("Item pcPerLicense must be positive");
        }
        if (item.getCount() == null || item.getCount() <= 0) {
            throw new IllegalArgumentException("Item count must be positive");
        }
    }

    protected int toWhiteAdminMonths(BusinessPeriod period) {
        return switch (period.unit()) {
            case MONTH -> period.amount();
            case YEAR -> period.amount() * 12;
            case DAY -> {
                if (period.amount() % 30 != 0) {
                    throw new IllegalArgumentException("WhiteAdmin pack term supports whole months only: " + period);
                }
                yield period.amount() / 30;
            }
        };
    }

    protected String resolveComment(OrderItemEntity item) {
        OrderEntity order = item.getOrderId() == null
                ? null
                : orderRepository.findById(item.getOrderId()).orElse(null);
        if (order == null) {
            return item.getOrderId() == null ? String.valueOf(item.getId()) : String.valueOf(item.getOrderId());
        }
        if (order.getWhiteAdminId() != null) {
            return String.valueOf(order.getWhiteAdminId());
        }
        if (order.getPortalId() != null) {
            return String.valueOf(order.getPortalId());
        }
        if (order.getUserComment() != null && !order.getUserComment().isBlank()) {
            return order.getUserComment().trim();
        }
        return String.valueOf(order.getId());
    }

    protected List<NameValuePair> extendParams(List<NameValuePair> base,
                                               OrderItemEntity item,
                                               ProductInfo product) {
        return base;
    }

    protected abstract String getEndpoint(ProductInfo product);

    protected List<GeneratedPackKey> parsePack(Document doc) {
        Element container = doc.selectFirst("#gen_pack_data");
        Element table = container != null ? container.selectFirst("table") : doc.selectFirst("table");
        if (table == null) {
            throw new RuntimeException("WhiteAdmin pack table not found");
        }
        Elements rows = table.select("tbody > tr");
        if (rows.isEmpty()) {
            rows = table.select("tr");
        }

        List<GeneratedPackKey> out = new ArrayList<>();
        for (Element row : rows) {
            Elements td = row.select("td");
            if (td.size() < 2) {
                continue;
            }
            String online = safe(td.get(0).text());
            String offline = safe(td.get(1).text());
            if (online.isBlank() && offline.isBlank()) {
                continue;
            }
            out.add(new GeneratedPackKey(
                    online.isBlank() ? null : online,
                    offline.isBlank() ? null : KeyMarkersUtils.removeMarkers(offline)
            ));
        }
        return out;
    }

    private OrderEntity resolveOrder(OrderItemEntity item) {
        if (item.getOrderId() == null) {
            return null;
        }
        return orderRepository.findByIdWithClient(item.getOrderId()).orElse(null);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    protected record GeneratedPackKey(String onlineKey, String offlineKey) {
    }

    protected AbstractWhiteAdminClient client() {
        return client;
    }

    private void triggerPostGenerationCatchupEnrichment(ProductInfo product,
                                                        Long sourceId) {
        if (!catchupEnabled || product == null || sourceId == null) {
            return;
        }
        String lockKey = "WHITE_ADMIN:" + product.brandId() + ":" + product.productId();
        AtomicBoolean lock = CATCHUP_LOCKS.computeIfAbsent(lockKey, ignored -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            CATCHUP_PENDING.computeIfAbsent(lockKey, ignored -> new AtomicInteger(0)).incrementAndGet();
            log.info("Defer WhiteAdmin catch-up enrich for product {}/{} due to overlap lock",
                    product.brandId(), product.productId());
            return;
        }
        try {
            Optional<Long> lastKnownExternalId = licenseRepository.findMaxExternalIdByProduct(
                    product.brandId(), product.productId());
            long latestExternalId = fetchLatestExternalId(product);

            if (lastKnownExternalId.isPresent() && latestExternalId <= lastKnownExternalId.get()) {
                log.info("Skip WhiteAdmin catch-up enrich for product {}/{}: latest={} <= knownMax={}",
                        product.brandId(), product.productId(), latestExternalId, lastKnownExternalId.get());
                return;
            }

            long fromId = latestExternalId;
            long toId;
            if (lastKnownExternalId.isPresent()) {
                toId = Math.max(0L, lastKnownExternalId.get() + 1L);
            } else {
                long window = Math.max(1L, catchupBootstrapWindow);
                toId = Math.max(0L, latestExternalId - window + 1L);
                log.info("wa_catchup_bootstrap_window_used product={}/{} latest={} window={} from={} to={}",
                        product.brandId(), product.productId(), latestExternalId, window, fromId, toId);
            }

            log.info("wa_catchup_range_built product={}/{} knownMax={} latest={} from={} to={}",
                    product.brandId(), product.productId(), lastKnownExternalId.orElse(null), latestExternalId, fromId, toId);

            if (fromId < toId) {
                log.info("Skip WhiteAdmin catch-up enrich for product {}/{}: empty range latest={} to={}",
                        product.brandId(), product.productId(), fromId, toId);
                return;
            }

            EnrichmentRequest request = new EnrichmentRequest(
                    UUID.randomUUID(),
                    sourceId,
                    EntityTypeEnum.LICENSE,
                    product.brandId(),
                    product.productId(),
                    null,
                    null,
                    fromId,
                    toId
            );
            enrichmentTaskManager.startEnrichment(request);
            log.info("Started WhiteAdmin post-generation catch-up enrich for product {}/{}: from={} to={} knownMax={}",
                    product.brandId(), product.productId(), fromId, toId, lastKnownExternalId.orElse(null));
            log.debug("wa_catchup_no_match_for_generated_key product={}/{} note='if any generated key remains without externalId after this run, investigate WA visibility lag'",
                    product.brandId(), product.productId());
        } finally {
            lock.set(false);
            AtomicInteger pending = CATCHUP_PENDING.get(lockKey);
            if (pending != null && pending.getAndSet(0) > 0) {
                log.info("Run deferred WhiteAdmin catch-up enrich for product {}/{} after overlap",
                        product.brandId(), product.productId());
                triggerPostGenerationCatchupEnrichment(product, sourceId);
            }
        }
    }

    protected abstract long fetchLatestExternalId(ProductInfo product);
}
