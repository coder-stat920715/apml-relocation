package com.apml.relocation.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends DomainException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "duplicate-resource", message);
    }
}
