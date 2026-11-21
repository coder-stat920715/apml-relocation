package com.apml.relocation.domain.enums;

public enum ConsignmentStatus {
    QUOTED, BOOKING_CONFIRMED, DOCUMENTS_VERIFIED, READY_FOR_LOADING, IN_TRANSIT, DELIVERED, CLOSED, CANCELLED;

    public boolean canTransitionTo(ConsignmentStatus next) {
        return switch (this) {
            case QUOTED -> next == BOOKING_CONFIRMED || next == CANCELLED;
            case BOOKING_CONFIRMED -> next == DOCUMENTS_VERIFIED || next == CANCELLED;
            case DOCUMENTS_VERIFIED -> next == READY_FOR_LOADING || next == CANCELLED;
            case READY_FOR_LOADING -> next == IN_TRANSIT || next == CANCELLED;
            case IN_TRANSIT -> next == DELIVERED;
            case DELIVERED -> next == CLOSED;
            case CLOSED, CANCELLED -> false;
        };
    }

    public boolean isCancellable() { return canTransitionTo(CANCELLED); }

    /** Statuses in which compliance documents may still be created or changed. */
    public boolean isDocumentEditable() {
        return this == QUOTED || this == BOOKING_CONFIRMED || this == DOCUMENTS_VERIFIED;
    }
}
