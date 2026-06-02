package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class LicenseSearchResolver implements SearchResolver<LicenseEntity> {
    private final LicenseRepository licenseRepository;
    private final OrderSearchResolver orderSearchResolver;
    private final ProductRegistry productRegistry;
    private final KeySearchNormalizer keySearchNormalizer;

    @Override
    public SearchEntityType entityType() {
        return SearchEntityType.LICENSE;
    }

    @Override
    public Class<LicenseEntity> modelType() {
        return LicenseEntity.class;
    }

    @Override
    public List<LicenseEntity> resolve(SearchQuery q) {
        Map<Long, LicenseEntity> out = new LinkedHashMap<>();
        if (q.lex() != null) {
            licenseRepository.findByExternalId(q.lex()).ifPresent(l -> out.put(l.getId(), l));
        }
        if (q.kid() != null) {
            licenseRepository.findByKey_Id(q.kid()).ifPresent(l -> out.put(l.getId(), l));
        }
        if (q.kon() != null && !q.kon().isBlank()) {
            String normalized = keySearchNormalizer.normalizeForSearch(q.kon());
            if (normalized != null) {
                boolean exactMatched = licenseRepository.findFirstByKey_OnlineKeyIgnoreCase(normalized)
                        .map(l -> {
                            out.put(l.getId(), l);
                            return true;
                        })
                        .orElse(false);
                if (!keySearchNormalizer.isFullKey(normalized) || !exactMatched) {
                    licenseRepository.findAllByOnlineOrOfflineContainsCi(normalized).forEach(l -> out.put(l.getId(), l));
                }
            }
        }
        if (q.kof() != null && !q.kof().isBlank()) {
            String normalized = keySearchNormalizer.normalizeForSearch(q.kof());
            if (normalized != null) {
                boolean exactMatched = licenseRepository.findFirstByKey_OfflineKeyIgnoreCase(normalized)
                        .map(l -> {
                            out.put(l.getId(), l);
                            return true;
                        })
                        .orElse(false);
                if (!keySearchNormalizer.isFullKey(normalized) || !exactMatched) {
                    licenseRepository.findAllByOnlineOrOfflineContainsCi(normalized).forEach(l -> out.put(l.getId(), l));
                }
            }
        }
        if (q.woid() != null || q.wzid() != null || q.wid2() != null || q.pid() != null
                || (q.comment() != null && !q.comment().isBlank())
                || q.orderId() != null
                || (q.productName() != null && !q.productName().isBlank())) {
            orderSearchResolver.resolve(q).forEach(order -> {
                for (LicenseEntity l : licenseRepository.findByOrderId(order.getId())) {
                    out.put(l.getId(), l);
                }
            });
        }
        int preFilterCount = out.size();
        ProductInfo productFilter = resolveProductFilter(q.productName());
        if (q.productName() != null && !q.productName().isBlank()) {
            log.info("License search product filter: token='{}' resolved={}/{} preFilterCount={}",
                    q.productName(),
                    productFilter == null ? "-" : productFilter.brandId(),
                    productFilter == null ? "-" : productFilter.productId(),
                    preFilterCount);
        }
        if (q.productName() != null && !q.productName().isBlank() && productFilter == null) {
            return List.of();
        }
        if (productFilter != null) {
            out.entrySet().removeIf(e -> e.getValue() == null
                    || e.getValue().getBrandId() == null
                    || e.getValue().getProductId() == null
                    || e.getValue().getBrandId() != productFilter.brandId()
                    || e.getValue().getProductId() != productFilter.productId());
            log.info("License search post product filter: token='{}' postFilterCount={}",
                    q.productName(),
                    out.size());
        }
        return new ArrayList<>(out.values());
    }

    private ProductInfo resolveProductFilter(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        return productRegistry.findProductByText(productName.trim()).orElse(null);
    }

}
