package com.zillya.timonfech.zillwrapper.core.regex;

import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriod;
import com.zillya.timonfech.zillwrapper.core.period.BusinessPeriodUnit;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor
public class NaturalDurationMatcher implements IMatcher<BusinessPeriod> {

    private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern UNIT_PATTERN =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*([\\p{L}]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final String YEAR_ALIAS = "y(?:ears?|rs?)?|р(?:[іо]к\\p{L}*)?|г(?:од\\p{L}*)?|л(?:ет\\p{L}*)?";
    private static final String MONTH_ALIAS = "m(?:on(?:ths?)?)?|м(?:[іе]с\\p{L}*)?";
    private static final String DAY_ALIAS = "d(?:ays?)?|д(?:е?н\\p{L}*)?";
    private static final Pattern YEAR_UNIT_PATTERN = Pattern.compile("^" + YEAR_ALIAS + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MONTH_UNIT_PATTERN = Pattern.compile("^" + MONTH_ALIAS + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DAY_UNIT_PATTERN = Pattern.compile("^" + DAY_ALIAS + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private MatchingException lastCause;

    @Override
    public Optional<BusinessPeriod> match(String text) throws MatchingException {
        if (text == null || text.isBlank()) {
            this.lastCause = fail(-1, "non-empty string", "null/empty", "");
            return Optional.empty();
        }

        Matcher bracketMatcher = BRACKETS_PATTERN.matcher(text);
        String targetText = bracketMatcher.find() ? bracketMatcher.group(1) : text;
        Matcher matcher = UNIT_PATTERN.matcher(targetText);

        boolean foundAtLeastOne = false;
        long totalDays = 0L;
        List<BusinessPeriod> parts = new ArrayList<>();

        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
                String unit = normalizeUnit(matcher.group(2));
                Optional<BusinessPeriod> part = toBusinessPeriod(value, unit);
                if (part.isPresent()) {
                    BusinessPeriod p = part.get();
                    parts.add(p);
                    totalDays += switch (p.unit()) {
                        case DAY -> p.amount();
                        case MONTH -> (long) p.amount() * 30L;
                        case YEAR -> (long) p.amount() * 365L;
                    };
                    foundAtLeastOne = true;
                }
            } catch (Exception e) {
                this.lastCause = new MatchingException(
                        "Mapping failed for part: " + matcher.group(),
                        matcher.start(),
                        UNIT_PATTERN.pattern(),
                        targetText,
                        matcher.group()
                );
                return Optional.empty();
            }
        }

        if (!foundAtLeastOne) {
            this.lastCause = fail(0, "Duration units (years, months, days)", text, "");
            return Optional.empty();
        }

        if (parts.size() == 1) {
            return Optional.of(parts.getFirst());
        }

        return Optional.of(new BusinessPeriod((int) Math.max(1L, totalDays), BusinessPeriodUnit.DAY));
    }

    @Override
    public MatchingException getCause() {
        return lastCause;
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.toLowerCase(Locale.ROOT).trim();
    }

    private Optional<BusinessPeriod> toBusinessPeriod(double value, String unit) {
        int amount = (int) Math.max(1L, Math.round(value));

        if (isYearUnit(unit)) {
            return Optional.of(new BusinessPeriod(amount, BusinessPeriodUnit.YEAR));
        }
        if (isMonthUnit(unit)) {
            return Optional.of(new BusinessPeriod(amount, BusinessPeriodUnit.MONTH));
        }
        if (isWeekUnit(unit)) {
            return Optional.of(new BusinessPeriod(Math.max(1, amount * 7), BusinessPeriodUnit.DAY));
        }
        if (isDayUnit(unit)) {
            return Optional.of(new BusinessPeriod(amount, BusinessPeriodUnit.DAY));
        }
        return Optional.empty();
    }

    private boolean isYearUnit(String unit) {
        return YEAR_UNIT_PATTERN.matcher(unit).matches();
    }

    private boolean isMonthUnit(String unit) {
        return MONTH_UNIT_PATTERN.matcher(unit).matches();
    }

    private boolean isWeekUnit(String unit) {
        return unit.startsWith("week")
                || unit.equals("w")
                || unit.startsWith("тиж")
                || unit.startsWith("нед");
    }

    private boolean isDayUnit(String unit) {
        return DAY_UNIT_PATTERN.matcher(unit).matches();
    }
}
