package com.zillya.timonfech.zillwrapper.core.regex.order;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductYamlPatternRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;

@Component
@RequiredArgsConstructor
public class KeyTypeAliasParser {
    private final ProductYamlPatternRegistry patternRegistry;

    public static final String ONLINE_PATTERN =
            "onl?i?n?e?|онл?а?й?н?";
    public static final String OFFLINE_PATTERN =
            "offl?i?n?e?|оф+л?а?й?н?";
    public static final String TOKEN_PATTERN = "(?:" + ONLINE_PATTERN + ")|(?:" + OFFLINE_PATTERN + ")";
    public static final String SEPARATOR_PATTERN = "and|/|\\+|,";

    public List<KeyType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(KeyType.ONLINE);
        }
        EnumSet<KeyType> result = EnumSet.noneOf(KeyType.class);
        for (var entry : patternRegistry.getKeyTypePatterns().entrySet()) {
            Matcher matcher = entry.getValue().matcher(raw);
            if (matcher.find()) {
                result.add(entry.getKey());
            }
        }
        return result.isEmpty() ? List.of(KeyType.ONLINE) : List.copyOf(result);
    }
}
