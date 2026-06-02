package com.zillya.timonfech.zillwrapper.core.search;

import com.zillya.timonfech.zillwrapper.apis.KeyMarkersUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class KeySearchNormalizer {

    private static final Map<Character, Character> HOMOGLYPHS = Map.ofEntries(
            Map.entry('А', 'A'),
            Map.entry('В', 'B'),
            Map.entry('С', 'C'),
            Map.entry('І', 'I'),
            Map.entry('Е', 'E'),
            Map.entry('К', 'K'),
            Map.entry('М', 'M'),
            Map.entry('Н', 'H'),
            Map.entry('О', 'O'),
            Map.entry('Р', 'P'),
            Map.entry('Т', 'T'),
            Map.entry('Х', 'X')
    );

    private static final Set<Integer> FULL_KEY_LENGTHS = Set.of(22, 32, 96, 236, 256);

    public String normalizeForSearch(String raw) {
        if (raw == null) {
            return null;
        }
        String withoutMarkers = KeyMarkersUtils.removeMarkers(raw);
        String collapsed = withoutMarkers.replaceAll("\\s+", "");
        if (collapsed.isBlank()) {
            return null;
        }
        String upper = collapsed.trim().toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            sb.append(HOMOGLYPHS.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public boolean isFullKey(String normalizedToken) {
        return normalizedToken != null && FULL_KEY_LENGTHS.contains(normalizedToken.length());
    }
}
