package com.apml.relocation.service;

import com.apml.relocation.domain.model.SurveyItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final BigDecimal gstRate;

    public PricingService(@Value("${apml.gst-rate:0.05}") BigDecimal gstRate) {
        this.gstRate = gstRate;
    }

    /** FOV (Transit Risk Coverage): 3.0% of declared value for household goods, 1.5% for vehicles. Zero at owner's risk. */
    public BigDecimal calculateFov(Collection<SurveyItem> items, boolean ownerRisk) {
        if (ownerRisk) return BigDecimal.ZERO.setScale(2);
        return items.stream()
                .map(i -> i.getDeclaredValue().multiply(i.getCategory().getFovRatePercent())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** GTA (SAC 996511) GST on freight + FOV; zero when the customer is GST exempted. */
    public BigDecimal calculateGst(boolean gstExempted, BigDecimal gross, BigDecimal fov) {
        if (gstExempted) return BigDecimal.ZERO.setScale(2);
        return gross.add(fov).multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal total(BigDecimal gross, BigDecimal fov, BigDecimal gst) {
        return gross.add(fov).add(gst).setScale(2, RoundingMode.HALF_UP);
    }
}
