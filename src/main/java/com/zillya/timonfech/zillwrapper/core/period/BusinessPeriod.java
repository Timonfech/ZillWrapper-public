package com.zillya.timonfech.zillwrapper.core.period;

public record BusinessPeriod(int amount, BusinessPeriodUnit unit) {
    public BusinessPeriod {
        if (amount <= 0) {
            throw new IllegalArgumentException("BusinessPeriod amount must be > 0");
        }
        if (unit == null) {
            throw new IllegalArgumentException("BusinessPeriod unit must not be null");
        }
    }
}

