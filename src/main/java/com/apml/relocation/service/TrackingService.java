package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.ReferenceType;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DemurrageCalculator;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.dto.TrackingDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.EnquiryRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cross-reference lookup, portal-style timeline and hub scan ingestion with its state side-effects. */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private static final Pattern GCN_SERIAL = Pattern.compile("^\\d{1,18}$");

    private final EnquiryRepository enquiries;
    private final ConsignmentRepository consignments;
    private final GoodsConsignmentNoteRepository gcns;
    private final PaymentService payments;
    private final CoordinatorService coordinators;
    private final TrackingEventService events;
    private final ConsignmentService consignmentService;
    private final Clock clock;

    private record Resolved(Consignment consignment, ReferenceType type) {}

    /** Accepts an enquiry no., CFR no., physical GCN serial or digital GC no. */
    @Transactional(readOnly = true)
    public TrackingDto.LookupResponse lookup(String reference) {
        Resolved resolved = resolve(reference.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Consignment for reference", reference));
        Consignment c = resolved.consignment();
        GoodsConsignmentNote g = gcns.findByConsignment(c).orElse(null);
        String digitalGcNo = g == null ? null : g.getDigitalGcNo();
        Enquiry e = c.getEnquiry();
        BigDecimal paid = payments.totalPaid(c);
        LocalDateTime arrival = c.getArrivedAtDestinationHubAt();
        LocalDateTime delivered = c.getDeliveredAt();

        TrackingDto.Identifiers identifiers = new TrackingDto.Identifiers(e.getEnquiryNo(), c.getCfrNo(),
                g == null ? null : g.getGcnSerialNo(), digitalGcNo);
        TrackingDto.ShipmentSummary shipment = new TrackingDto.ShipmentSummary(c.getStatus(), e.getCustomerName(),
                e.getOriginCity(), e.getDestinationCity(), e.getMovementType(), e.getTotalCft(), c.getTotalPayableAmount(),
                paid, c.getTotalPayableAmount().subtract(paid), arrival,
                arrival == null ? null : arrival.toLocalDate().plusDays(DemurrageCalculator.FREE_DAYS),
                c.getDemurrageDays(), c.getDemurrageAmount(), delivered,
                delivered == null ? null : delivered.plus(ItemCategory.FOUR_WHEELER.getClaimWindow()),
                delivered == null ? null : delivered.plus(ItemCategory.HOUSEHOLD_GOODS.getClaimWindow()));
        return new TrackingDto.LookupResponse(resolved.type(), identifiers, shipment,
                payments.list(c.getCfrNo()).stream().map(ApmlMapper::toResponse).toList(),
                coordinators.list(c).stream().map(ApmlMapper::toResponse).toList(),
                timeline(c, digitalGcNo));
    }

    @Transactional(readOnly = true)
    public TrackingDto.Timeline timeline(String digitalGcNo) {
        GoodsConsignmentNote g = findByDigitalGcNo(digitalGcNo);
        return timeline(g.getConsignment(), g.getDigitalGcNo());
    }

    /**
     * Records a hub scan. Codes must not move backwards. ARRIVED_DESTINATION_HUB stamps the hub arrival (starting the
     * demurrage grace period); DELIVERED runs the full delivery gate (payment proof + POD) and opens the claim window.
     */
    @Transactional
    public TrackingEvent record(String digitalGcNo, TrackingDto.EventRequest request) {
        GoodsConsignmentNote g = findByDigitalGcNo(digitalGcNo);
        Consignment c = g.getConsignment();
        if (c.getStatus() != ConsignmentStatus.IN_TRANSIT) {
            throw new BusinessRuleException("Tracking events can only be recorded while the consignment is IN_TRANSIT");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime timestamp = request.eventTimestamp() == null ? now : request.eventTimestamp();
        if (timestamp.isAfter(now)) {
            throw new BusinessRuleException("eventTimestamp cannot be in the future");
        }
        TrackingEvent last = events.latest(c);
        if (last != null && request.eventCode().ordinal() < last.getEventCode().ordinal()) {
            throw new BusinessRuleException("Event " + request.eventCode() + " is out of sequence after " + last.getEventCode());
        }
        TrackingEventService.Details details = new TrackingEventService.Details(request.hubCity(), request.eventDescription(),
                request.truckVehicleNo(), request.recordedByAgentId());
        if (request.eventCode() == TrackingEventCode.ARRIVED_DESTINATION_HUB) {
            consignmentService.recordHubArrival(c.getCfrNo(), timestamp, details);
            return events.latest(c);
        }
        if (request.eventCode() == TrackingEventCode.DELIVERED) {
            consignmentService.deliver(c.getCfrNo(), details);
            return events.latest(c);
        }
        return events.append(c, request.eventCode(), timestamp, details);
    }

    private Optional<Resolved> resolve(String ref) {
        if (ref.startsWith("EQ")) {
            return enquiries.findByEnquiryNo(ref).flatMap(consignments::findByEnquiry)
                    .map(c -> new Resolved(c, ReferenceType.ENQUIRY_NO));
        }
        if (ref.startsWith("CFR")) {
            return consignments.findByCfrNo(ref).map(c -> new Resolved(c, ReferenceType.CFR_NO));
        }
        if (GCN_SERIAL.matcher(ref).matches()) {
            return gcns.findByGcnSerialNo(Long.parseLong(ref)).map(g -> new Resolved(g.getConsignment(), ReferenceType.GCN_SERIAL_NO));
        }
        if (ref.startsWith("GC")) {
            return gcns.findByDigitalGcNo(ref).map(g -> new Resolved(g.getConsignment(), ReferenceType.DIGITAL_GC_NO));
        }
        return Optional.empty();
    }

    private GoodsConsignmentNote findByDigitalGcNo(String digitalGcNo) {
        return gcns.findByDigitalGcNo(digitalGcNo.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Digital GC", digitalGcNo));
    }

    /** The progress bar fills up to the furthest milestone reached; each milestone shows its first event time. */
    private TrackingDto.Timeline timeline(Consignment c, String digitalGcNo) {
        List<TrackingEvent> list = events.timeline(c);
        int current = list.stream().mapToInt(e -> e.getMilestone().ordinal()).max().orElse(-1);
        List<TrackingDto.MilestoneProgress> progress = Arrays.stream(TrackingMilestone.values())
                .map(m -> new TrackingDto.MilestoneProgress(m, m.ordinal() <= current, m.ordinal() == current,
                        list.stream().filter(e -> e.getMilestone() == m).map(TrackingEvent::getEventTimestamp)
                                .findFirst().orElse(null)))
                .toList();
        TrackingMilestone currentMilestone = current < 0 ? null : TrackingMilestone.values()[current];
        return new TrackingDto.Timeline(digitalGcNo, c.getCfrNo(), currentMilestone, progress,
                list.stream().map(ApmlMapper::toResponse).toList());
    }
}
