package com.zillya.timonfech.zillwrapper.core.regex.flags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ParameterFlagParser {

    private final List<ParameterFlagDefinition> definitions;
    private final Pattern pattern;

    public ParameterFlagParser(List<ParameterFlagDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        this.pattern = Pattern.compile(
                "-(" + aliasesAlternation() + ")\\s+([A-Za-z0-9]{1,16}(?:-[A-Za-z0-9]{1,16})*)\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );
    }

    public ParameterFlagParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParameterFlagParseResult(Map.of());
        }
        Map<String, String> values = new HashMap<>();
        for (ParameterFlagMatch match : findKnownFlags(text)) {
            values.put(match.key(), match.value());
        }
        return new ParameterFlagParseResult(Map.copyOf(values));
    }

    public List<ParameterFlagMatch> findKnownFlags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        java.util.ArrayList<ParameterFlagMatch> matches = new java.util.ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String alias = matcher.group(1);
            String value = matcher.group(2);
            definitions.stream()
                    .filter(def -> def.matchesAlias(alias))
                    .filter(def -> def.valuePattern().matcher(value).matches())
                    .findFirst()
                    .ifPresent(def -> matches.add(new ParameterFlagMatch(
                            def.key(),
                            value.toLowerCase(),
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
        return pattern.matcher(text).replaceAll("").trim();
    }

    private String aliasesAlternation() {
        return definitions.stream()
                .flatMap(definition -> definition.aliases().stream())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }
}
