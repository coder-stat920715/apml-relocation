package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final BigDecimal TOTAL = new BigDecimal("10000.00");

    @Test
    void fourOrMoreDaysBeforePacking_noDeduction_fullRefund() {
        var q = CancellationPolicy.quote(TODAY.plusDays(4), TODAY, TOTAL, new BigDecimal("3000.00"));
        assertThat(q.penaltyPercent()).isEqualByComparingTo("0");
        assertThat(q.deduction()).isEqualByComparingTo("0.00");
        assertThat(q.refund()).isEqualByComparingTo("3000.00");
    }

    @Test
    void threeDaysBeforePacking_tenPercentOfTotalDeducted_excessRefunded() {
        var q = CancellationPolicy.quote(TODAY.plusDays(3), TODAY, TOTAL, new BigDecimal("3000.00"));
        assertThat(q.deduction()).isEqualByComparingTo("1000.00");
        assertThat(q.refund()).isEqualByComparingTo("2000.00");
    }

    @Test
    void onPackingDay_tenPercentApplies() {
        var q = CancellationPolicy.quote(TODAY, TODAY, TOTAL, TOTAL);
        assertThat(q.daysBeforePacking()).isZero();
        assertThat(q.deduction()).isEqualByComparingTo("1000.00");
        assertThat(q.refund()).isEqualByComparingTo("9000.00");
    }

    @Test
    void paidLessThanPenalty_deductionCappedAtPaid_noRefund() {
        var q = CancellationPolicy.quote(TODAY.plusDays(1), TODAY, TOTAL, new BigDecimal("700.00"));
        assertThat(q.deduction()).isEqualByComparingTo("700.00");
        assertThat(q.refund()).isEqualByComparingTo("0.00");
    }

    @Test
    void afterPackingDate_rejected() {
        assertThrows(BusinessRuleException.class,
                () -> CancellationPolicy.quote(TODAY.minusDays(1), TODAY, TOTAL, TOTAL));
    }
}
