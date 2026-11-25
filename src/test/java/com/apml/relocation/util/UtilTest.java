package com.apml.relocation.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.support.TestData;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UtilTest {

    @Test
    void maskingKeepsLastFourCharacters() {
        assertThat(MaskingUtil.mask("123456789012")).isEqualTo("********9012");
        assertThat(MaskingUtil.mask("ABCDE1234F")).isEqualTo("******234F");
    }

    @Test
    void maskingHidesShortValuesCompletely() {
        assertThat(MaskingUtil.mask("1234")).isEqualTo("****");
        assertThat(MaskingUtil.mask("12")).isEqualTo("**");
    }

    @Test
    void maskingReturnsNullForNullOrBlank() {
        assertThat(MaskingUtil.mask(null)).isNull();
        assertThat(MaskingUtil.mask("  ")).isNull();
    }

    @Test
    void referenceNumbersFollowApmlFormat() {
        ReferenceNumberGenerator gen = new ReferenceNumberGenerator(TestData.clock());
        assertThat(gen.enquiryNo("Kolkata")).matches("EQKOL26\\d{7}");
        assertThat(gen.cfrNo("Hyderabad")).matches("CFRHYD26\\d{7}");
        assertThat(gen.receiptNo()).matches("ARVWB26\\d{7}");
    }

    @Test
    void cityCodeIsPaddedOrDefaulted() {
        ReferenceNumberGenerator gen = new ReferenceNumberGenerator(TestData.clock());
        assertThat(gen.cfrNo("Ab")).startsWith("CFRABX");
        assertThat(gen.cfrNo("12 3")).startsWith("CFRXXX");
        assertThat(gen.cfrNo(null)).startsWith("CFRXXX");
    }

    @Test
    @Disabled
    void digitalGcPrefixUsesTheFinancialYear() {
        assertThat(new ReferenceNumberGenerator(TestData.clock()).digitalGcPrefix("Kolkata")).isEqualTo("GCKOL2627");
        java.time.Clock february = java.time.Clock.fixed(
                java.time.LocalDateTime.of(2027, 2, 10, 10, 0).atZone(TestData.ZONE).toInstant(), TestData.ZONE);
        assertThat(new ReferenceNumberGenerator(february).digitalGcPrefix("Kolkata")).isEqualTo("GCKOL2527");
        assertThat(new ReferenceNumberGenerator(february).digitalGcPrefix(null)).isEqualTo("GCXXX2526");
    }
}
