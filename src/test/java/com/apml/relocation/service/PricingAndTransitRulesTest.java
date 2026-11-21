package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.model.SurveyItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingAndTransitRulesTest {

    private final PricingService pricing = new PricingService(new BigDecimal("0.05"));

    private static SurveyItem item(ItemCategory c, String declared) {
        SurveyItem i = new SurveyItem();
        i.setCategory(c);
        i.setDeclaredValue(new BigDecimal(declared));
        return i;
    }

    @Test
    void fovIsOnePointFivePercentForVehiclesAndThreePercentForHouseholdGoods() {
        BigDecimal fov = pricing.calculateFov(List.of(
                item(ItemCategory.TWO_WHEELER, "5000"), item(ItemCategory.CARTON_BOX, "5000")), false);
        assertThat(fov).isEqualByComparingTo("225.00"); // 75 + 150
    }

    @Test
    void ownerRiskMeansNoFov() {
        assertThat(pricing.calculateFov(List.of(item(ItemCategory.FOUR_WHEELER, "100000")), true)).isEqualByComparingTo("0");
    }

    @Test
    void gstIsZeroWhenExempted() {
        assertThat(pricing.calculateGst(true, new BigDecimal("7000"), new BigDecimal("225"))).isEqualByComparingTo("0");
        assertThat(pricing.calculateGst(false, new BigDecimal("7000"), new BigDecimal("225"))).isEqualByComparingTo("361.25");
    }

    @Test
    void transitEstimates() {
        assertThat(MovementType.FULL_TRUCK_LOAD.estimateDays(1500)).isEqualTo(5);   // ceil(1500/370)
        assertThat(MovementType.CUBES.estimateDays(1500)).isEqualTo(8);              // 6 + 2
        assertThat(MovementType.PART_LOAD_SHARED.estimateDays(1500)).isEqualTo(11);  // 8 + 3
    }
}
