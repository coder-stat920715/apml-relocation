package com.apml.relocation.domain.model;

import com.apml.relocation.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * APML cancellation policy (pure, stateless):
 *  - >= 4 days before packing : 0% deduction, full refund of amount paid
 *  - 0..3 days before packing : 10% of total booking amount deducted, excess paid is refunded
 */
public final class CancellationPolicy {

    private CancellationPolicy() {}

    public record Quote(long daysBeforePacking, BigDecimal penaltyPercent, BigDecimal deduction, BigDecimal refund) {}

    public static Quote quote(LocalDate packingDate, LocalDate today, BigDecimal totalPayable, BigDecimal paid) {
        long days = ChronoUnit.DAYS.between(today, packingDate);
        if (days < 0) {
            throw new BusinessRuleException("Cancellation is not permitted after the packing date");
        }
        BigDecimal percent = days >= 4 ? BigDecimal.ZERO : BigDecimal.TEN;
        BigDecimal penalty = totalPayable.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal deduction = penalty.min(paid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refund = paid.subtract(deduction).setScale(2, RoundingMode.HALF_UP);
        return new Quote(days, percent, deduction, refund);
    }
}
