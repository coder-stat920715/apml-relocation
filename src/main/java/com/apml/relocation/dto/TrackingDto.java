package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.ReferenceType;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TrackingDto {
    private TrackingDto() {}

    /** Hub scan; the milestone is derived from the event code. Defaults: timestamp = now, hub and text per code. */
    public record EventRequest(
            @NotNull TrackingEventCode eventCode,
            LocalDateTime eventTimestamp,
            @Size(max = 100) String hubCity,
            @Size(max = 500) String eventDescription,
            String truckVehicleNo,
            String recordedByAgentId) {}

    public record EventResponse(Long eventId, String digitalGcNo, TrackingMilestone milestone, TrackingEventCode eventCode,
                                LocalDate eventDate, LocalDateTime eventTimestamp, String hubCity, String eventDescription,
                                String truckVehicleNo, String recordedByAgentId) {}

    public record MilestoneProgress(TrackingMilestone milestone, boolean reached, boolean current, LocalDateTime reachedAt) {}

    public record Timeline(String digitalGcNo, String cfrNo, TrackingMilestone currentMilestone,
                           List<MilestoneProgress> milestones, List<EventResponse> events) {}

    public record Identifiers(String enquiryNo, String cfrNo, Long gcnSerialNo, String digitalGcNo) {}

    public record ShipmentSummary(ConsignmentStatus status, String customerName, String originCity, String destinationCity,
                                  MovementType movementType, BigDecimal totalCft, BigDecimal totalPayableAmount,
                                  BigDecimal amountPaid, BigDecimal balanceDue, LocalDateTime arrivedAtDestinationHubAt,
                                  LocalDate demurrageFreeUntil, Integer demurrageDays, BigDecimal demurrageAmount,
                                  LocalDateTime deliveredAt, LocalDateTime vehicleClaimDeadline,
                                  LocalDateTime householdClaimDeadline) {}

    public record LookupResponse(ReferenceType matchedBy, Identifiers identifiers, ShipmentSummary shipment,
                                 List<PaymentDto.Response> receipts, List<CoordinatorDto.Response> coordinators,
                                 Timeline timeline) {}
}
