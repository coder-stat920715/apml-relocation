package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PaymentDto {
    private PaymentDto() {}

    public record Request(String receiptNo, @NotNull @DecimalMin("0.01") BigDecimal amount,
                          @NotNull PaymentMode mode, String referenceNo) {}

    public record Response(String receiptNo, String cfrNo, BigDecimal amount, PaymentMode mode, String referenceNo,
                           LocalDateTime receivedAt) {}
}
