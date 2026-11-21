package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.LiabilitySection;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.UnloadingBy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public final class GcnDto {
    private GcnDto() {}

    /** Every field is optional so the note can be issued first and completed later (dispatch re-validates). */
    public record IssueRequest(
            Long gcnSerialNo,
            String cfrReferenceNo,
            LiabilitySection liabilitySection,
            @DecimalMin("0.01") BigDecimal declaredGoodsValue,
            DeliveryMode deliveryMode,
            UnloadingBy unloadingBy,
            PaymentBillingType paymentBillingType,
            @Min(1) Integer totalPackages,
            @DecimalMin("0.01") BigDecimal actualWeightKg,
            @DecimalMin("0.01") BigDecimal chargedWeightKg,
            @DecimalMin("0.01") BigDecimal lengthFt,
            @DecimalMin("0.01") BigDecimal breadthFt,
            @DecimalMin("0.01") BigDecimal heightFt,
            Map<PackingMaterial, @Min(1) Integer> packingMaterialsUsed,
            String ibaCode, String carriageActRegNo, String gstin, String sacCode) {}

    public record DispatchRequest(String truckVehicleNo, String cubeNo, String sealNo) {}

    public record Response(
            String cfrNo, Long gcnSerialNo, LocalDateTime gcnDate, String ibaCode, String carriageActRegNo, String gstin,
            String sacCode, String cfrReferenceNo, LiabilitySection liabilitySection, BigDecimal declaredGoodsValue,
            DeliveryMode deliveryMode, UnloadingBy unloadingBy, PaymentBillingType paymentBillingType,
            Integer totalPackages, BigDecimal actualWeightKg, BigDecimal chargedWeightKg, BigDecimal lengthFt,
            BigDecimal breadthFt, BigDecimal heightFt, Map<PackingMaterial, Integer> packingMaterialsUsed,
            Map<PackingMaterial, Integer> returnableMaterialsDueSameDay, String truckVehicleNo, String cubeNo,
            String sealNo, LocalDateTime dispatchedAt) {}
}
