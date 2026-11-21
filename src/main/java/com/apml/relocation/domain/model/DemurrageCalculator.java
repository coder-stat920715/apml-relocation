package com.apml.relocation.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * APML demurrage: one free day after arrival at the destination hub; thereafter INR 5.00 per day per package OR
 * INR 5.00 per day per quintal (100 kg) of charged weight, whichever is higher.
 */
public final class DemurrageCalculator {

    public static final BigDecimal RATE = new BigDecimal("5.00");
    public static final long FREE_DAYS = 1;

    private DemurrageCalculator() {}

    public record Result(long extraDays, BigDecimal byPackages, BigDecimal byWeight, BigDecimal amount) {}

    public static Result calculate(LocalDate arrivedOn, LocalDate collectedOn, int packages, BigDecimal chargedWeightKg) {
        long elapsed = ChronoUnit.DAYS.between(arrivedOn, collectedOn);
        long extra = Math.max(0, elapsed - FREE_DAYS);
        BigDecimal quintals = chargedWeightKg == null ? BigDecimal.ZERO
                : chargedWeightKg.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal days = BigDecimal.valueOf(extra);
        BigDecimal byPackages = RATE.multiply(BigDecimal.valueOf(packages)).multiply(days).setScale(2, RoundingMode.HALF_UP);
        BigDecimal byWeight = RATE.multiply(quintals).multiply(days).setScale(2, RoundingMode.HALF_UP);
        return new Result(extra, byPackages, byWeight, byPackages.max(byWeight));
    }
}
