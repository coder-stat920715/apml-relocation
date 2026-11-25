package com.apml.relocation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class PodDto {
    private PodDto() {}

    /**
     * Handover checklist. Flags left out count as not verified. consigneeIdReference may carry an ID number
     * but is only ever stored masked (XXXX-XXXX-1234).
     */
    public record Request(
            Boolean originalRcVerified,
            Boolean originalInsuranceVerified,
            Boolean originalPucVerified,
            Boolean consigneeIdVerified,
            @Size(max = 30) String consigneeIdReference,
            Boolean paymentReceiptsVerified,
            Boolean unpackedAndInspectedBeforeSignOff,
            @NotBlank String podSignedBy,
            LocalDateTime podSignedAt,
            @Size(max = 1000) String deliveryRemarks,
            Boolean damageNotedOnPod) {}

    public record Response(
            String cfrNo, boolean originalRcVerified, boolean originalInsuranceVerified, boolean originalPucVerified,
            boolean consigneeIdVerified, String consigneeIdMaskedRef, boolean paymentReceiptsVerified,
            boolean unpackedAndInspectedBeforeSignOff, String podSignedBy, LocalDateTime podSignedAt,
            String deliveryRemarks, boolean damageNotedOnPod) {}
}
