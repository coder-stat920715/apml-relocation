package com.apml.relocation.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends DomainException {
    public BusinessRuleException(String message) {
        super(HttpStatus.valueOf(422), "business-rule-violation", message);
    }
}
