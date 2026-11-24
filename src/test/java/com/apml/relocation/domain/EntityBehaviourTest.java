package com.apml.relocation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.KycDocument;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EntityBehaviourTest {

    @Test
    void enquiryTotalCftMultipliesVolumeByQuantity() {
        SurveyItem bike = TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000");
        SurveyItem cartons = TestData.item(2, ItemCategory.CARTON_BOX, "1000", "0");
        cartons.setQuantity(3);
        Enquiry e = TestData.enquiry(bike, cartons);
        assertThat(e.getTotalCft()).isEqualByComparingTo("40.00");
        assertThat(bike.getEnquiry()).isSameAs(e);
    }

    @Test
    void enquiryWithoutItemsHasZeroCft() {
        assertThat(TestData.enquiry().getTotalCft()).isEqualByComparingTo("0");
    }

    @Test
    void enquiryDetectsCategoriesAndComputesDeadlines() {
        Enquiry e = TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "1", "1"));
        assertThat(e.hasCategory(ItemCategory.TWO_WHEELER)).isTrue();
        assertThat(e.hasCategory(ItemCategory.FOUR_WHEELER)).isFalse();
        assertThat(e.getValidUntil()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(e.getMoveDeadline()).isEqualTo(LocalDate.of(2026, 12, 21));
    }

    @Test
    void kycToStringNeverExposesTheDocumentNumber() {
        KycDocument d = new KycDocument();
        d.setType(KycType.AADHAAR);
        d.setDocumentNumber("123456789012");
        d.setVerified(true);
        assertThat(d.toString()).contains("AADHAAR").contains("verified=true").doesNotContain("123456789012");
    }

    @Test
    void declarationReturnsCoverPerItemAndZeroWhenOmitted() {
        MasterArticleDeclaration d = new MasterArticleDeclaration();
        d.getLines().add(new ArticleLine(1L, new BigDecimal("5000"), new BigDecimal("4000")));
        d.getLines().add(new ArticleLine(2L, new BigDecimal("100"), new BigDecimal("100")));
        assertThat(d.coverageFor(1L)).isEqualByComparingTo("4000");
        assertThat(d.coverageFor(2L)).isEqualByComparingTo("100");
        assertThat(d.coverageFor(3L)).isEqualByComparingTo("0");
    }
}
