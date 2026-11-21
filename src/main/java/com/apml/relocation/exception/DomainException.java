package com.apml.relocation.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getExtensions() { return extensions; }

    protected void extension(String key, Object value) { extensions.put(key, value); }
}
