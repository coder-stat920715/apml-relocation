package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.MovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TransitDto {
    private TransitDto() {}

    public record LogRequest(@NotBlank String location, String remarks) {}

    public record LogResponse(String location, String remarks, ConsignmentStatus statusAtLog, LocalDateTime loggedAt) {}

    public record EstimateResponse(String cfrNo, MovementType movementType, int distanceKm, int kmPerDay, int bufferDays,
                                   int estimatedTransitDays, LocalDate expectedDeliveryDate) {}

    public record ClaimRequest(@NotNull Long surveyItemId, @NotNull DamageType damageType, String description,
                               @NotNull @DecimalMin("0.01") BigDecimal claimedAmount) {}

    public record ClaimResponse(Long claimId, Long surveyItemId, DamageType damageType, BigDecimal claimedAmount,
                                BigDecimal approvedAmount, ClaimStatus status, LocalDateTime lodgedAt) {}
}
