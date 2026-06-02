package com.zillya.timonfech.zillwrapper.core.entities.product;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import com.zillya.timonfech.zillwrapper.core.interfaces.IProduct;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record ProductInfo(
        int productId,
        int brandId,
        String groupId,
        int version,
        Pattern regex,
        Map<String, String> names,
        Map<String, String> properties,
        List<KeyType> keyTypes
) implements IProduct {

    public boolean matches(String text) {
        return this.regex().matcher(text).matches();
    }

    public String getName(@Nullable Locale locale, boolean full, boolean withVersion) throws IllegalArgumentException {
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String type = full ? "full" : "short";
        String key = lang + "_" + type;

        String baseName = this.names().getOrDefault(key, this.names().getOrDefault("names." + key, null));
        if (baseName == null) {
            baseName = this.names().entrySet().stream()
                    .filter(e -> e.getKey().endsWith("_" + type))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(this.names().values().stream().findFirst().orElse(""));
        }
        if (baseName.isEmpty()) {
            throw new IllegalArgumentException("No name found for locale " + locale + " and full " + full);
        }

        if (withVersion) {
            String versionAsText = String.valueOf(this.version());
            if (!baseName.contains(versionAsText)) {
                return baseName + " " + versionAsText;
            }
        }
        return baseName;
    }

    public Map<String, String> getProperties(@Nullable Locale locale, @Nullable KeyType keyType) {
        if (locale == null && keyType == null) {
            return this.properties();
        }
        String language = locale == null ? null : locale.getLanguage().toLowerCase(Locale.ROOT);
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> entry : this.properties().entrySet()) {
            String key = entry.getKey();
            String lower = key.toLowerCase(Locale.ROOT);
            if (!matchesLocale(lower, language)) {
                continue;
            }
            if (!matchesKeyType(lower, keyType)) {
                continue;
            }
            out.put(key, entry.getValue());
        }
        return out;
    }

    private boolean matchesLocale(String key, @Nullable String language) {
        if (language == null || language.isBlank()) {
            return true;
        }
        if (key.startsWith("uk_") || key.startsWith("ua_")) {
            return "uk".equals(language);
        }
        if (key.startsWith("en_")) {
            return "en".equals(language);
        }
        if (key.startsWith("ru_")) {
            return "ru".equals(language);
        }
        return true;
    }

    private boolean matchesKeyType(String key, @Nullable KeyType keyType) {
        if (keyType == null) {
            return true;
        }
        boolean mentionsOnline = key.contains("online");
        boolean mentionsOffline = key.contains("offline");
        if (!mentionsOnline && !mentionsOffline) {
            return true;
        }
        return switch (keyType) {
            case ONLINE -> mentionsOnline;
            case OFFLINE -> mentionsOffline;
        };
    }

    @Override
    public int getBrandId() {
        return this.brandId;
    }

    @Override
    public int getProductId() {
        return this.productId;
    }

    @Override
    public String getGroupId() {
        return this.groupId;
    }
}
