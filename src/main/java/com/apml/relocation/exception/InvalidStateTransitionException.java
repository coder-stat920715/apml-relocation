package com.apml.relocation.exception;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends DomainException {
    public InvalidStateTransitionException(ConsignmentStatus from, ConsignmentStatus to) {
        super(HttpStatus.CONFLICT, "invalid-state-transition", "Cannot move consignment from " + from + " to " + to);
        extension("from", from);
        extension("to", to);
    }
}
