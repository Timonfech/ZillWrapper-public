package com.zillya.timonfech.zillwrapper.core.entities.product;

import com.zillya.timonfech.zillwrapper.core.repos.ProductRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRegistry {

    private final ProductRepository productRepository;

    private final Map<Integer, ProductInfo> productsById = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public synchronized void refreshCache() {
        log.info("Refreshing products cache from Database...");

        List<ProductEntity> entities = productRepository.findAll();

        Map<Integer, ProductInfo> newCache = entities.stream()
                .map(this::mapToDomainModel)
                .collect(Collectors.toMap(ProductInfo::productId, p -> p));

        // Атомарно обновляем кэш
        productsById.clear();
        productsById.putAll(newCache);

        log.info("Loaded {} products into memory.", productsById.size());
    }

    public Optional<ProductInfo> getProductById(int productId) {
        return Optional.ofNullable(productsById.get(productId));
    }

    /**
     * Resolve source type for product by productId + brandId.
     * Rule: if product has non-blank groupId in products registry -> DINO_ADMIN.
     */
    public Optional<SourceType> resolveSourceType(int productId, int brandId) {
        Optional<ProductInfo> productOpt = getProductById(productId)
                .filter(product -> product.brandId() == brandId);
        if (productOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductInfo product = productOpt.get();
        if (product.groupId() != null && !product.groupId().isBlank()) {
            return Optional.of(SourceType.DINO_ADMIN);
        }
        return Optional.of(SourceType.WHITE_ADMIN);
    }

    public List<ProductInfo> getAllProducts() {
        return List.copyOf(productsById.values());
    }

    public Optional<ProductInfo> findProductByText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return productsById.values().stream()
                .filter(p -> p.matches(text.trim()))
                .max((a, b) -> compareMatchPriority(a, b, text.trim()));
    }

    private int compareMatchPriority(ProductInfo a, ProductInfo b, String text) {
        int aVersion = matchScoreByVersion(a, text);
        int bVersion = matchScoreByVersion(b, text);
        if (aVersion != bVersion) {
            return Integer.compare(aVersion, bVersion);
        }
        if (a.version() != b.version()) {
            return Integer.compare(a.version(), b.version());
        }
        if (a.brandId() != b.brandId()) {
            return Integer.compare(a.brandId(), b.brandId());
        }
        return Integer.compare(a.productId(), b.productId());
    }

    private int matchScoreByVersion(ProductInfo product, String text) {
        try {
            Matcher matcher = product.regex().matcher(text);
            if (!matcher.matches()) {
                return 0;
            }
            try {
                String versionGroup = matcher.group("version");
                if (versionGroup != null && !versionGroup.isBlank()) {
                    return 2;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return 1;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private ProductInfo mapToDomainModel(ProductEntity entity) {
        // Превращаем String в скомпилированный Pattern
        Pattern compiledRegex = Pattern.compile(
                entity.getRegexPattern(),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );

        return new ProductInfo(
                entity.getProductId(),
                entity.getBrandId(),
                entity.getGroupId(),
                entity.getVersion(),
                compiledRegex,
                entity.getNames(),
                entity.getProperties(),
                entity.getKeyTypes()
        );
    }
}
