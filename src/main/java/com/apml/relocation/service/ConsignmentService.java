package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DemurrageCalculator;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.dto.PodDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.InvalidStateTransitionException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.EnquiryRepository;
import com.apml.relocation.repository.TransitLogRepository;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsignmentService {

    private final ConsignmentRepository consignments;
    private final EnquiryRepository enquiries;
    private final TransitLogRepository transitLogs;
    private final PricingService pricing;
    private final PaymentService payments;
    private final ComplianceService compliance;
    private final GcnService gcn;
    private final DeliveryPodService pods;
    private final TrackingEventService trackingEvents;
    private final ReferenceNumberGenerator numbers;
    private final Clock clock;

    @Transactional
    public Consignment create(ConsignmentDto.CreateRequest req) {
        Enquiry e = enquiries.findByEnquiryNo(req.enquiryNo())
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", req.enquiryNo()));
        if (e.getStatus() == EnquiryStatus.CONVERTED) throw new BusinessRuleException("Enquiry already converted to a consignment");
        if (e.getStatus() != EnquiryStatus.APPROVED) throw new BusinessRuleException("Enquiry must be approved before a CFR is generated");

        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(e.getValidUntil())) {
            throw new BusinessRuleException("Quotation validity (15 days from submission) expired on " + e.getValidUntil());
        }
        if (req.packingDate().isBefore(today) || req.loadingDate().isBefore(req.packingDate())) {
            throw new BusinessRuleException("Packing date must not be in the past and loading date must not precede packing");
        }
        if (req.loadingDate().isAfter(e.getMoveDeadline())) {
            throw new BusinessRuleException("Move must occur within 30 days of enquiry submission (by " + e.getMoveDeadline() + ")");
        }

        String cfr = req.cfrNo();
        if (cfr == null || cfr.isBlank()) {
            do { cfr = numbers.cfrNo(e.getOriginCity()); } while (consignments.existsByCfrNo(cfr));
        } else if (consignments.existsByCfrNo(cfr)) {
            throw new DuplicateResourceException("Consignment already exists: " + cfr);
        }

        BigDecimal gross = req.grossFreightAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fov = pricing.calculateFov(e.getItems(), req.ownerRisk());
        BigDecimal gst = pricing.calculateGst(e.isGstExempted(), gross, fov);

        Consignment c = new Consignment();
        c.setCfrNo(cfr);
        c.setEnquiry(e);
        c.setPackingDate(req.packingDate());
        c.setLoadingDate(req.loadingDate());
        c.setGrossFreightAmount(gross);
        c.setFovChargeAmount(fov);
        c.setGstAmount(gst);
        c.setTotalPayableAmount(pricing.total(gross, fov, gst));
        c.setOwnerRisk(req.ownerRisk());
        c.setStatus(ConsignmentStatus.QUOTED);
        c = consignments.save(c);

        e.setStatus(EnquiryStatus.CONVERTED);
        enquiries.save(e);
        compliance.initVehicleChecklists(c);
        log.info("CFR {} quoted: total INR {}", cfr, c.getTotalPayableAmount());
        return c;
    }

    @Transactional(readOnly = true)
    public Consignment get(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }

    /** QUOTED -> BOOKING_CONFIRMED : needs >= 10% advance. */
    @Transactional
    public Consignment confirmBooking(String cfrNo) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.BOOKING_CONFIRMED);
        payments.assertAdvancePaid(c);
        return move(c, ConsignmentStatus.BOOKING_CONFIRMED);
    }

    /** BOOKING_CONFIRMED -> DOCUMENTS_VERIFIED : KYC, vehicle documents and Master Article List must pass. */
    @Transactional
    public Consignment verifyDocuments(String cfrNo) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.DOCUMENTS_VERIFIED);
        compliance.assertCompliant(c);
        return move(c, ConsignmentStatus.DOCUMENTS_VERIFIED);
    }

    /** DOCUMENTS_VERIFIED -> READY_FOR_LOADING : 100% payment + every compliance rule. */
    @Transactional
    public Consignment markReadyForLoading(String cfrNo) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.READY_FOR_LOADING);
        payments.assertFullyPaid(c);
        compliance.assertCompliant(c);
        return move(c, ConsignmentStatus.READY_FOR_LOADING);
    }

    @Transactional
    public Consignment dispatch(String cfrNo) {
        return dispatch(cfrNo, null);
    }

    /**
     * READY_FOR_LOADING -> IN_TRANSIT. Requires 100% payment and a Goods Consignment Note carrying truck no., seal no.,
     * liability section, delivery mode and billing type PAID (cube no. as well for CUBES shipments).
     */
    @Transactional
    public Consignment dispatch(String cfrNo, GcnDto.DispatchRequest details) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.IN_TRANSIT);
        payments.assertFullyPaid(c);
        GoodsConsignmentNote g = gcn.applyDispatchDetails(c, details);
        payments.syncBillingType(c);
        gcn.assertDispatchReady(c, g);
        gcn.markDispatched(g);
        c = move(c, ConsignmentStatus.IN_TRANSIT);
        TransitLog t = new TransitLog();
        t.setConsignment(c);
        t.setLocation(c.getEnquiry().getOriginCity());
        t.setRemarks("Dispatched in truck " + g.getTruckVehicleNo() + ", seal " + g.getSealNo());
        t.setStatusAtLog(ConsignmentStatus.IN_TRANSIT);
        t.setLoggedAt(LocalDateTime.now(clock));
        transitLogs.save(t);
        return c;
    }

    /** Records arrival at the destination hub; the 1-day free demurrage period starts here. */
    @Transactional
    public Consignment recordHubArrival(String cfrNo, LocalDateTime arrivedAt) {
        return recordHubArrival(cfrNo, arrivedAt, null);
    }

    @Transactional
    public Consignment recordHubArrival(String cfrNo, LocalDateTime arrivedAt, TrackingEventService.Details details) {
        Consignment c = get(cfrNo);
        if (c.getStatus() != ConsignmentStatus.IN_TRANSIT) {
            throw new BusinessRuleException("Hub arrival can only be recorded while the consignment is IN_TRANSIT");
        }
        if (c.getArrivedAtDestinationHubAt() != null) {
            throw new BusinessRuleException("Hub arrival was already recorded at " + c.getArrivedAtDestinationHubAt());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime when = arrivedAt == null ? now : arrivedAt;
        if (when.isAfter(now)) throw new BusinessRuleException("arrivedAt cannot be in the future");
        c.setArrivedAtDestinationHubAt(when);
        c = consignments.save(c);
        TransitLog t = new TransitLog();
        t.setConsignment(c);
        t.setLocation(c.getEnquiry().getDestinationCity() + " hub");
        t.setRemarks("Arrived at destination hub");
        t.setStatusAtLog(ConsignmentStatus.IN_TRANSIT);
        t.setLoggedAt(when);
        transitLogs.save(t);
        trackingEvents.append(c, TrackingEventCode.ARRIVED_DESTINATION_HUB, when, details);
        return c;
    }

    @Transactional
    public Consignment deliver(String cfrNo) {
        return deliver(cfrNo, null, null);
    }

    @Transactional
    public Consignment deliver(String cfrNo, TrackingEventService.Details details) {
        return deliver(cfrNo, null, details);
    }

    /**
     * IN_TRANSIT -> DELIVERED. Needs verified payment proof and a complete delivery POD (recorded from the optional
     * request or beforehand). Stamps deliveredAt (opening the 12h / 72h claim windows), computes demurrage and
     * appends the DELIVERED tracking event.
     */
    @Transactional
    public Consignment deliver(String cfrNo, PodDto.Request pod, TrackingEventService.Details details) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.DELIVERED);
        if (pod != null) {
            pods.record(c, pod);
        }
        payments.assertPaymentProof(c);
        pods.assertDeliverable(c);
        LocalDateTime now = LocalDateTime.now(clock);
        c.setDeliveredAt(now);
        if (c.getArrivedAtDestinationHubAt() != null) {
            GoodsConsignmentNote g = gcn.find(c);
            int packages = g == null || g.getTotalPackages() == null ? 0 : g.getTotalPackages();
            var weight = g == null ? null : (g.getChargedWeightKg() != null ? g.getChargedWeightKg() : g.getActualWeightKg());
            DemurrageCalculator.Result d = DemurrageCalculator.calculate(
                    c.getArrivedAtDestinationHubAt().toLocalDate(), now.toLocalDate(), packages, weight);
            c.setDemurrageDays((int) d.extraDays());
            c.setDemurrageAmount(d.amount());
            log.info("CFR {} delivered with {} extra day(s) of demurrage: INR {}", cfrNo, d.extraDays(), d.amount());
        }
        c = move(c, ConsignmentStatus.DELIVERED);
        trackingEvents.append(c, TrackingEventCode.DELIVERED, now, details);
        return c;
    }

    @Transactional
    public Consignment close(String cfrNo) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.CLOSED);
        return move(c, ConsignmentStatus.CLOSED);
    }

    @Transactional(readOnly = true)
    public CancellationPolicy.Quote previewCancellation(String cfrNo) {
        Consignment c = get(cfrNo);
        if (!c.getStatus().isCancellable()) throw new InvalidStateTransitionException(c.getStatus(), ConsignmentStatus.CANCELLED);
        return payments.cancellationQuote(c);
    }

    @Transactional
    public Consignment cancel(String cfrNo, String reason) {
        Consignment c = get(cfrNo);
        requireTransition(c, ConsignmentStatus.CANCELLED);
        CancellationPolicy.Quote q = payments.cancellationQuote(c);
        c.setCancellationCharge(q.deduction());
        c.setRefundAmount(q.refund());
        c.setCancelledAt(LocalDateTime.now(clock));
        c.setCancellationReason(reason);
        log.info("CFR {} cancelled {} day(s) before packing: deduction INR {}, refund INR {}",
                cfrNo, q.daysBeforePacking(), q.deduction(), q.refund());
        return move(c, ConsignmentStatus.CANCELLED);
    }

    private static void requireTransition(Consignment c, ConsignmentStatus target) {
        if (!c.getStatus().canTransitionTo(target)) throw new InvalidStateTransitionException(c.getStatus(), target);
    }

    private Consignment move(Consignment c, ConsignmentStatus target) {
        log.info("CFR {}: {} -> {}", c.getCfrNo(), c.getStatus(), target);
        c.setStatus(target);
        return consignments.save(c);
    }
}
