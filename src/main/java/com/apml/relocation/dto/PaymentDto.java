package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PaymentDto {
    private PaymentDto() {}

    /** referenceNo is kept as a backwards-compatible alias of chequeOrTxnRef. */
    public record Request(String receiptNo, @NotNull @DecimalMin("0.01") BigDecimal amount,
                          @NotNull PaymentMode mode, String referenceNo, String chequeOrTxnRef, String branchCode,
                          String generatedByAgentId, Boolean verified) {}

    public record Response(String receiptNo, String cfrNo, BigDecimal amount, PaymentMode mode, String referenceNo,
                           String chequeOrTxnRef, String branchCode, String generatedByAgentId, boolean verified,
                           LocalDateTime receivedAt) {}
}
