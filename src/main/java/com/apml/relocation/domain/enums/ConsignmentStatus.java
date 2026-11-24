package com.apml.relocation.domain.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ConsignmentStatus {
    QUOTED, BOOKING_CONFIRMED, DOCUMENTS_VERIFIED, READY_FOR_LOADING, IN_TRANSIT, DELIVERED, CLOSED, CANCELLED;

    private static final Map<ConsignmentStatus, Set<ConsignmentStatus>> NEXT = new EnumMap<>(ConsignmentStatus.class);

    static {
        NEXT.put(QUOTED, EnumSet.of(BOOKING_CONFIRMED, CANCELLED));
        NEXT.put(BOOKING_CONFIRMED, EnumSet.of(DOCUMENTS_VERIFIED, CANCELLED));
        NEXT.put(DOCUMENTS_VERIFIED, EnumSet.of(READY_FOR_LOADING, CANCELLED));
        NEXT.put(READY_FOR_LOADING, EnumSet.of(IN_TRANSIT, CANCELLED));
        NEXT.put(IN_TRANSIT, EnumSet.of(DELIVERED));
        NEXT.put(DELIVERED, EnumSet.of(CLOSED));
        NEXT.put(CLOSED, EnumSet.noneOf(ConsignmentStatus.class));
        NEXT.put(CANCELLED, EnumSet.noneOf(ConsignmentStatus.class));
    }

    public boolean canTransitionTo(ConsignmentStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isCancellable() { return canTransitionTo(CANCELLED); }

    /** Statuses in which compliance documents may still be created or changed. */
    public boolean isDocumentEditable() {
        return this == QUOTED || this == BOOKING_CONFIRMED || this == DOCUMENTS_VERIFIED;
    }
}
