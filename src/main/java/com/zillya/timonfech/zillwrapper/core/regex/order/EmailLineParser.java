package com.zillya.timonfech.zillwrapper.core.regex.order;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EmailLineParser {

    private static final Pattern EMAIL_FINDER_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public boolean hasEmail(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return EMAIL_FINDER_PATTERN.matcher(line).find();
    }

    public int findEmailLineIndex(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            if (hasEmail(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public List<String> extractEmails(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        Matcher matcher = EMAIL_FINDER_PATTERN.matcher(line);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(values);
    }
}
