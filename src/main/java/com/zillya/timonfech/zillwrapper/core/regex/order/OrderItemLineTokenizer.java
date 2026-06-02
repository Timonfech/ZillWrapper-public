package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.entities.product.ProductInfo;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OrderItemLineTokenizer {

    private static final Pattern SPEC_START_PATTERN = Pattern.compile(
            "^\\d+\\s*(?:пк|pc)?\\s*[\\\\/]\\s*\\d+.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private final ProductRegistry productRegistry;

    public boolean looksLikeItemLine(String line) {
        return tokenize(line).isPresent();
    }

    public java.util.Optional<OrderItemLineParts> tokenize(String line) {
        if (line == null || line.isBlank()) {
            return java.util.Optional.empty();
        }
        List<ProductInfo> products = productRegistry.getAllProducts();
        if (products == null || products.isEmpty()) {
            return java.util.Optional.empty();
        }

        return products.stream()
                .map(product -> matchProductAtLineStart(product, line))
                .flatMap(java.util.Optional::stream)
                .max(Comparator
                        .comparing((OrderItemLineParts parts) -> hasMatchedVersion(parts.product(), parts.productText()))
                        .thenComparing((OrderItemLineParts parts) -> looksLikeSpecStart(parts.specText()))
                        .thenComparingInt(parts -> parts.productText().length()));
    }

    public OrderItemLineParts tokenizeOrThrow(String line) throws OrderParseException {
        List<ProductInfo> products = productRegistry.getAllProducts();
        if (products == null || products.isEmpty()) {
            throw new OrderParseException("Product registry is empty");
        }
        return tokenize(line).orElseThrow(() -> new OrderParseException("Unknown product in order item line: " + line));
    }

    private java.util.Optional<OrderItemLineParts> matchProductAtLineStart(ProductInfo product, String line) {
        String candidate = "";
        String[] tokens = line.trim().split("\\s+");
        OrderItemLineParts best = null;
        for (String token : tokens) {
            candidate = candidate.isBlank() ? token : candidate + " " + token;
            if (product.matches(candidate)) {
                String spec = line.substring(candidate.length()).trim();
                if (!spec.isBlank()) {
                    OrderItemLineParts current = new OrderItemLineParts(product, candidate, spec);
                    if (best == null) {
                        best = current;
                        continue;
                    }
                    boolean currentVersionMatched = hasMatchedVersion(current.product(), current.productText());
                    boolean bestVersionMatched = hasMatchedVersion(best.product(), best.productText());
                    if (currentVersionMatched && !bestVersionMatched) {
                        best = current;
                        continue;
                    }
                    if (!currentVersionMatched && bestVersionMatched) {
                        continue;
                    }
                    boolean currentSpecLike = looksLikeSpecStart(current.specText());
                    boolean bestSpecLike = looksLikeSpecStart(best.specText());
                    if (currentSpecLike && !bestSpecLike) {
                        best = current;
                        continue;
                    }
                    if (currentSpecLike == bestSpecLike && current.productText().length() > best.productText().length()) {
                        best = current;
                    }
                }
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    private boolean looksLikeSpecStart(String spec) {
        if (spec == null || spec.isBlank()) {
            return false;
        }
        return SPEC_START_PATTERN.matcher(spec.trim()).matches();
    }

    private boolean hasMatchedVersion(ProductInfo product, String candidate) {
        if (product == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        Matcher matcher = product.regex().matcher(candidate);
        if (!matcher.matches()) {
            return false;
        }
        try {
            String version = matcher.group("version");
            return version != null && !version.isBlank();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
