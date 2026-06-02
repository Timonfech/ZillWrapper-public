package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FlagParser {

    private final List<FlagDefinition> definitions;
    private final Pattern knownFlagPattern;

    public FlagParser(List<FlagDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        this.knownFlagPattern = Pattern.compile(
                "-(" + aliasesAlternation() + ")\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );
    }

    public FlagParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            return new FlagParseResult(Map.of());
        }

        Map<String, Boolean> values = new HashMap<>();
        for (FlagMatch match : findKnownFlags(text)) {
            values.put(match.key(), match.value());
        }
        return new FlagParseResult(Map.copyOf(values));
    }

    public List<FlagMatch> findKnownFlags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        java.util.ArrayList<FlagMatch> matches = new java.util.ArrayList<>();
        Matcher matcher = knownFlagPattern.matcher(text);
        while (matcher.find()) {
            String alias = matcher.group(1);
            definitions.stream()
                    .filter(definition -> definition.matches(alias))
                    .findFirst()
                    .ifPresent(definition -> matches.add(new FlagMatch(
                            definition.key(),
                            definition.valueFor(alias),
                            matcher.start(),
                            matcher.end()
                    )));
        }
        return List.copyOf(matches);
    }

    public String removeKnownFlags(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return knownFlagPattern.matcher(text).replaceAll("").trim();
    }

    private String aliasesAlternation() {
        return definitions.stream()
                .flatMap(definition -> definition.aliases().stream())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    public record FlagMatch(String key, Boolean value, int start, int end) {
    }
}
