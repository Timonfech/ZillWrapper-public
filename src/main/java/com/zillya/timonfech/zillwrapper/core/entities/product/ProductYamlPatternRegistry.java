package com.zillya.timonfech.zillwrapper.core.entities.product;

import com.zillya.timonfech.zillwrapper.core.entities.KeyType;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProductYamlPatternRegistry {

    private static final String DEFAULT_ONLINE = "onl?i?n?e?|онл?а?й?н?";
    private static final String DEFAULT_OFFLINE = "offl?i?n?e?|оф+л?а?й?н?";
    private static final String DEFAULT_KEY_PATTERN = "[a-f0-9]{4,64}";
    private static final String DEFAULT_OFF_SUFFIX = "(?:\\+\\s*(?:off|офф))";
    private static final String DEFAULT_SEPARATORS = "[,\\n\\r]+";

    @Getter
    private final Map<KeyType, Pattern> keyTypePatterns = new EnumMap<>(KeyType.class);

    @Getter
    private final Map<String, LicenseCommentPatternProfile> licenseCommentProfiles = new LinkedHashMap<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        keyTypePatterns.clear();
        licenseCommentProfiles.clear();

        InputStream is = getClass().getClassLoader().getResourceAsStream("products.yaml");
        if (is == null) {
            applyDefaults();
            log.warn("products.yaml not found, pattern registry loaded with defaults.");
            return;
        }

        Object rootObj = new Yaml().load(is);
        if (!(rootObj instanceof Map<?, ?> root)) {
            applyDefaults();
            log.warn("products.yaml invalid root, pattern registry loaded with defaults.");
            return;
        }

        loadKeyTypes(root);
        loadLegacyCommentProfiles(root);

        if (keyTypePatterns.isEmpty()) {
            keyTypePatterns.put(KeyType.ONLINE, Pattern.compile(DEFAULT_ONLINE, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
            keyTypePatterns.put(KeyType.OFFLINE, Pattern.compile(DEFAULT_OFFLINE, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
        }
        if (licenseCommentProfiles.isEmpty()) {
            addDefaultProfiles();
        }
    }

    public LicenseCommentPatternProfile profile(String name) {
        if (name == null) {
            return licenseCommentProfiles.get("whiteadmin");
        }
        return licenseCommentProfiles.getOrDefault(name.toLowerCase(), licenseCommentProfiles.get("whiteadmin"));
    }

    @SuppressWarnings("unchecked")
    private void loadKeyTypes(Map<?, ?> root) {
        Object keyTypesObj = root.get("keyTypes");
        if (!(keyTypesObj instanceof List<?> list)) {
            return;
        }
        for (Object entryObj : list) {
            if (!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            String name = asString(entry.get("name"));
            String pattern = asString(entry.get("pattern"));
            if (name == null || pattern == null) {
                continue;
            }
            try {
                KeyType keyType = KeyType.valueOf(name.trim().toUpperCase());
                keyTypePatterns.put(keyType, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
            } catch (Exception ex) {
                log.warn("Invalid key type regex config name='{}': {}", name, ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLegacyCommentProfiles(Map<?, ?> root) {
        Object profilesObj = root.get("licenseCommentPatterns");
        if (!(profilesObj instanceof Map<?, ?> profilesMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : profilesMap.entrySet()) {
            String name = Objects.toString(entry.getKey(), "").trim().toLowerCase();
            if (!(entry.getValue() instanceof Map<?, ?> cfg)) {
                continue;
            }
            String keyPattern = asString(cfg.get("keyPattern"));
            String offlineSuffixPattern = asString(cfg.get("offlineSuffixPattern"));
            String separatorsPattern = asString(cfg.get("separatorsPattern"));
            if (name.isBlank() || keyPattern == null || offlineSuffixPattern == null || separatorsPattern == null) {
                continue;
            }
            try {
                licenseCommentProfiles.put(name, new LicenseCommentPatternProfile(
                        Pattern.compile(keyPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                        Pattern.compile(offlineSuffixPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                        Pattern.compile(separatorsPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
                ));
            } catch (Exception ex) {
                log.warn("Invalid license comment pattern profile '{}': {}", name, ex.getMessage());
            }
        }
    }

    private void applyDefaults() {
        keyTypePatterns.put(KeyType.ONLINE, Pattern.compile(DEFAULT_ONLINE, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
        keyTypePatterns.put(KeyType.OFFLINE, Pattern.compile(DEFAULT_OFFLINE, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
        addDefaultProfiles();
    }

    private void addDefaultProfiles() {
        LicenseCommentPatternProfile fallback = new LicenseCommentPatternProfile(
                Pattern.compile(DEFAULT_KEY_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                Pattern.compile(DEFAULT_OFF_SUFFIX, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                Pattern.compile(DEFAULT_SEPARATORS, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
        );
        licenseCommentProfiles.put("whiteadmin", fallback);
        licenseCommentProfiles.put("dino", fallback);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    public record LicenseCommentPatternProfile(
            Pattern keyPattern,
            Pattern offlineSuffixPattern,
            Pattern separatorsPattern
    ) {}
}
