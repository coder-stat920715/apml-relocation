package com.apml.relocation.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ComplianceViolationException extends DomainException {
    private final List<String> violations;

    public ComplianceViolationException(List<String> violations) {
        super(HttpStatus.valueOf(422), "compliance-violation", "Compliance check failed: " + violations.size() + " issue(s)");
        this.violations = List.copyOf(violations);
        extension("violations", this.violations);
    }

    public List<String> getViolations() { return violations; }
}
