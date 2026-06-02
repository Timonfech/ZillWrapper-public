package com.zillya.timonfech.zillwrapper.init;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductEntity;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import com.zillya.timonfech.zillwrapper.core.repos.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogSyncService {

    private final ProductRepository productRepository;
    private final ProductRegistry productRegistry;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    @Transactional
    @SuppressWarnings("unchecked")
    public void syncFromYamlIfMissing() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("products.yaml");
        if (is == null) {
            log.warn("products.yaml not found, product sync skipped.");
            return;
        }

        Yaml yaml = new Yaml();
        Object rootObj = yaml.load(is);
        if (!(rootObj instanceof Map<?, ?> root)) {
            log.warn("products.yaml has invalid root structure, product sync skipped.");
            return;
        }

        Object productsObj = root.get("products");
        if (!(productsObj instanceof Map<?, ?> productsMap)) {
            log.warn("products.yaml has no 'products' section, product sync skipped.");
            return;
        }

        Object listObj = productsMap.get("list");
        if (!(listObj instanceof List<?> list)) {
            log.warn("products.yaml has no 'products.list' section, product sync skipped.");
            return;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (Object itemObj : list) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }

            Integer productId = asInt(item.get("productId"));
            Integer brandId = asInt(item.get("brandId"));
            Integer version = asInt(item.get("version"));
            String groupId = asString(item.get("groupId"));
            String regex = asString(item.get("regex"));

            if (productId == null || brandId == null || version == null || regex == null) {
                log.warn("Skipping malformed product in yaml: productId={}, brandId={}, version={}, regex={}",
                        productId, brandId, version, regex);
                skipped++;
                continue;
            }

            Map<String, String> names = readStringMap(item.get("names"));
            Map<String, String> properties = readStringMap(item.get("properties"));
            List<KeyType> keyTypes = readKeyTypes(item.get("keyTypes"));

            ProductEntity entity = productRepository.findById(productId).orElse(null);
            if (entity == null) {
                entity = new ProductEntity(
                        productId,
                        brandId,
                        version,
                        groupId,
                        regex,
                        names,
                        properties,
                        keyTypes
                );
                productRepository.save(entity);
                inserted++;
                continue;
            }

            boolean changed = false;
            if (entity.getBrandId() != brandId) {
                entity.setBrandId(brandId);
                changed = true;
            }
            if (entity.getVersion() != version) {
                entity.setVersion(version);
                changed = true;
            }
            if (!java.util.Objects.equals(entity.getGroupId(), groupId)) {
                entity.setGroupId(groupId);
                changed = true;
            }
            if (!java.util.Objects.equals(entity.getRegexPattern(), regex)) {
                entity.setRegexPattern(regex);
                changed = true;
            }
            if (!java.util.Objects.equals(entity.getNames(), names)) {
                entity.setNames(names);
                changed = true;
            }
            if (!java.util.Objects.equals(entity.getProperties(), properties)) {
                entity.setProperties(properties);
                changed = true;
            }
            if (!java.util.Objects.equals(entity.getKeyTypes(), keyTypes)) {
                entity.setKeyTypes(keyTypes);
                changed = true;
            }

            if (changed) {
                productRepository.save(entity);
                updated++;
            } else {
                skipped++;
            }
        }

        if (inserted > 0 || updated > 0) {
            productRegistry.refreshCache();
        }
        log.info("Product yaml sync completed: inserted={}, updated={}, skipped={}", inserted, updated, skipped);
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readStringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : rawMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return out;
    }

    private List<KeyType> readKeyTypes(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<KeyType> out = new ArrayList<>();
        for (Object it : rawList) {
            if (it == null) {
                continue;
            }
            String keyType = String.valueOf(it).trim().toUpperCase(Locale.ROOT);
            try {
                out.add(KeyType.valueOf(keyType));
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown keyType '{}' in products.yaml, skipping", keyType);
            }
        }
        return out;
    }
}
