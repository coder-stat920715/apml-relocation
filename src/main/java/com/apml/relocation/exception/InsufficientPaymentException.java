package com.apml.relocation.exception;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public class InsufficientPaymentException extends DomainException {
    public InsufficientPaymentException(String message, BigDecimal required, BigDecimal paid) {
        super(HttpStatus.PAYMENT_REQUIRED, "insufficient-payment", message);
        extension("requiredAmount", required);
        extension("paidAmount", paid);
        extension("shortfall", required.subtract(paid));
    }
}
