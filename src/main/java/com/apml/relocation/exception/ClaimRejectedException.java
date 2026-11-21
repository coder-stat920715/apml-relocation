package com.apml.relocation.exception;

import org.springframework.http.HttpStatus;

public class ClaimRejectedException extends DomainException {
    public ClaimRejectedException(String reason) {
        super(HttpStatus.valueOf(422), "claim-rejected", reason);
    }
}
