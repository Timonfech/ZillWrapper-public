package com.zillya.timonfech.zillwrapper.core.period;

import org.springframework.stereotype.Component;

@Component
public class BusinessPeriodNormalizer {

    public long toDays(BusinessPeriod period) {
        if (period == null) {
            return 0L;
        }
        return switch (period.unit()) {
            case DAY -> period.amount();
            case MONTH -> (long) period.amount() * 30L;
            case YEAR -> (long) period.amount() * 365L;
        };
    }

    public int toYearsRoundedUp(BusinessPeriod period) {
        if (period == null) {
            return 1;
        }
        return switch (period.unit()) {
            case YEAR -> period.amount();
            case MONTH -> (int) Math.max(1, Math.ceil(period.amount() / 12.0));
            case DAY -> (int) Math.max(1, Math.ceil(period.amount() / 365.0));
        };
    }
}

