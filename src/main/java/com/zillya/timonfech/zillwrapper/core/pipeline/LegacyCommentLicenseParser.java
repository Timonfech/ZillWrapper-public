package com.zillya.timonfech.zillwrapper.core.pipeline;

import com.zillya.timonfech.zillwrapper.core.entities.product.ProductYamlPatternRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

@Component
@RequiredArgsConstructor
public class LegacyCommentLicenseParser {

    private final ProductYamlPatternRegistry patternRegistry;

    public ParseResult parse(String comment, String profileName) {
        if (comment == null || comment.isBlank()) {
            return new ParseResult(List.of(), List.of(), Set.of());
        }
        ProductYamlPatternRegistry.LicenseCommentPatternProfile profile = patternRegistry.profile(profileName);
        List<String> chunks = splitBySeparators(comment, profile);

        List<Token> tokens = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        Set<String> normalizedKeys = new HashSet<>();

        for (String chunk : chunks) {
            String normalized = normalize(chunk);
            if (normalized == null) {
                continue;
            }
            Matcher keyMatcher = profile.keyPattern().matcher(normalized);
            if (!keyMatcher.find()) {
                unmatched.add(normalized);
                continue;
            }
            String rawKey = keyMatcher.group();
            String key = normalizeKey(rawKey);
            boolean offline = detectOffline(normalized, profile, keyMatcher.end());
            String tail = keyMatcher.end() < normalized.length()
                    ? normalized.substring(keyMatcher.end()).trim()
                    : null;
            tokens.add(new Token(key, offline, tail, normalized));
            normalizedKeys.add(key);
        }

        return new ParseResult(List.copyOf(tokens), List.copyOf(unmatched), Set.copyOf(normalizedKeys));
    }

    private List<String> splitBySeparators(String comment, ProductYamlPatternRegistry.LicenseCommentPatternProfile profile) {
        List<String> result = new ArrayList<>();
        String[] parts = profile.separatorsPattern().split(comment);
        for (String part : parts) {
            if (part != null) {
                result.add(part);
            }
        }
        return result;
    }

    private boolean detectOffline(String line, ProductYamlPatternRegistry.LicenseCommentPatternProfile profile, int start) {
        if (start >= line.length()) {
            return false;
        }
        String tail = line.substring(start);
        return profile.offlineSuffixPattern().matcher(tail).find();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replace("\r\n", "\n").trim();
        return text.isBlank() ? null : text;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    public record Token(String key, boolean offline, String tail, String rawChunk) {}

    public record ParseResult(List<Token> tokens, List<String> unmatchedChunks, Set<String> normalizedKeys) {}
}
