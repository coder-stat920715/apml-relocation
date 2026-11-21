package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ConsignmentDto {
    private ConsignmentDto() {}

    public record CreateRequest(
            @NotBlank String enquiryNo,
            String cfrNo,
            @NotNull LocalDate packingDate,
            @NotNull LocalDate loadingDate,
            @NotNull @DecimalMin("0.01") BigDecimal grossFreightAmount,
            boolean ownerRisk) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record Response(String cfrNo, String enquiryNo, ConsignmentStatus status, LocalDate packingDate,
                           LocalDate loadingDate, BigDecimal totalCft, BigDecimal grossFreightAmount,
                           BigDecimal fovChargeAmount, BigDecimal gstAmount, BigDecimal totalPayableAmount,
                           BigDecimal amountPaid, BigDecimal balanceDue, BigDecimal requiredAdvance, boolean ownerRisk,
                           LocalDateTime deliveredAt, LocalDateTime cancelledAt, BigDecimal cancellationCharge,
                           BigDecimal refundAmount, LocalDateTime arrivedAtDestinationHubAt, Integer demurrageDays,
                           BigDecimal demurrageAmount) {}

    /** arrivedAt is optional (defaults to now) so a hub scan can be back-dated. */
    public record HubArrivalRequest(LocalDateTime arrivedAt) {}

    public record CancellationQuoteResponse(String cfrNo, long daysBeforePacking, BigDecimal penaltyPercent,
                                            BigDecimal deduction, BigDecimal refund) {}
}
