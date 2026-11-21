package com.apml.relocation.domain.enums;

import java.math.BigDecimal;
import java.time.Duration;

public enum ItemCategory {
    TWO_WHEELER("1.5", 12), FOUR_WHEELER("1.5", 12), CARTON_BOX("3.0", 72), HOUSEHOLD_GOODS("3.0", 72);

    private final BigDecimal fovRatePercent;
    private final Duration claimWindow;

    ItemCategory(String fovRatePercent, int claimWindowHours) {
        this.fovRatePercent = new BigDecimal(fovRatePercent);
        this.claimWindow = Duration.ofHours(claimWindowHours);
    }

    public BigDecimal getFovRatePercent() { return fovRatePercent; }
    public Duration getClaimWindow() { return claimWindow; }
    public boolean isVehicle() { return this == TWO_WHEELER || this == FOUR_WHEELER; }
}
