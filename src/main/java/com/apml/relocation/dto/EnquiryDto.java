package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EnquiryDto {
    private EnquiryDto() {}

    public record ItemRequest(
            @NotNull ItemCategory category,
            @NotBlank String description,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal volumeCft,
            boolean crateRequired,
            @NotNull @DecimalMin("0.00") BigDecimal declaredValue,
            @DecimalMin("0.00") BigDecimal riskCoverageAvailed) {}

    public record CreateRequest(
            String enquiryNo,
            @NotBlank String customerName,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number") String mobile,
            @Email String email,
            String gstNo,
            boolean gstExempted,
            @NotBlank String originCity,
            @NotBlank String destinationCity,
            @NotBlank String pickupAddress,
            @NotBlank String deliveryAddress,
            @NotNull MovementType movementType,
            @Positive int distanceKm,
            @NotEmpty @Valid List<ItemRequest> items,
            @Valid AddressDto consignorAddress,
            @Valid AddressDto consigneeAddress,
            String consigneeName,
            String consigneeMobile) {}

    public record ItemResponse(Long id, ItemCategory category, String description, int quantity, BigDecimal volumeCft,
                               boolean crateRequired, BigDecimal declaredValue, BigDecimal riskCoverageAvailed) {}

    public record Response(String enquiryNo, EnquiryStatus status, String customerName, String mobile, String email,
                           String gstNo, boolean gstExempted, String originCity, String destinationCity,
                           String pickupAddress, String deliveryAddress, MovementType movementType, int distanceKm,
                           BigDecimal totalCft, LocalDate submittedOn, LocalDate validUntil, LocalDate moveDeadline,
                           List<ItemResponse> items, AddressDto consignorAddress, AddressDto consigneeAddress,
                           String consigneeName, String consigneeMobile) {}
}
