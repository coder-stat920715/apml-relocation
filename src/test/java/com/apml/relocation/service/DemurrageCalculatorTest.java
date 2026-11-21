package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.model.DemurrageCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DemurrageCalculatorTest {

    private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 10);

    @Test
    void collectionWithinOneFreeDay_noCharge() {
        assertThat(DemurrageCalculator.calculate(ARRIVAL, ARRIVAL, 2, new BigDecimal("100")).amount()).isEqualByComparingTo("0");
        assertThat(DemurrageCalculator.calculate(ARRIVAL, ARRIVAL.plusDays(1), 2, new BigDecimal("100")).amount()).isEqualByComparingTo("0");
    }

    @Test
    void perPackageRateWins_whenHigher() {
        // 4 days after arrival => 3 chargeable days; 2 packages -> 5*2*3 = 30; 100 kg = 1 quintal -> 5*1*3 = 15
        var r = DemurrageCalculator.calculate(ARRIVAL, ARRIVAL.plusDays(4), 2, new BigDecimal("100"));
        assertThat(r.extraDays()).isEqualTo(3);
        assertThat(r.byPackages()).isEqualByComparingTo("30.00");
        assertThat(r.byWeight()).isEqualByComparingTo("15.00");
        assertThat(r.amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void perQuintalRateWins_whenHigher() {
        // 750 kg = 7.5 quintals -> 5*7.5*3 = 112.50 beats 2 packages (30)
        var r = DemurrageCalculator.calculate(ARRIVAL, ARRIVAL.plusDays(4), 2, new BigDecimal("750"));
        assertThat(r.amount()).isEqualByComparingTo("112.50");
    }

    @Test
    void missingWeight_fallsBackToPackageRate() {
        assertThat(DemurrageCalculator.calculate(ARRIVAL, ARRIVAL.plusDays(3), 3, null).amount()).isEqualByComparingTo("30.00");
    }
}
