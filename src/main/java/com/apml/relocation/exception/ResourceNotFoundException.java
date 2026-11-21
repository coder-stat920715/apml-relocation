package com.apml.relocation.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resource, String key) {
        super(HttpStatus.NOT_FOUND, "resource-not-found", resource + " not found: " + key);
    }
}
