package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(new BigDecimal("0.05"));

    @Test
    void totalAddsFreightFovAndGst() {
        assertThat(pricing.total(new BigDecimal("6775"), new BigDecimal("225"), new BigDecimal("350.50")))
                .isEqualByComparingTo("7350.50");
    }
}
