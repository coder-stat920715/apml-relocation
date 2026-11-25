package com.apml.relocation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ReferenceType;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.dto.TrackingDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.DeliveryPodService;
import com.apml.relocation.service.TrackingService;
import com.apml.relocation.support.TestData;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class TrackingServiceIntegrationTest {

    @Autowired TrackingService tracking;
    @Autowired ConsignmentService consignments;
    @Autowired DeliveryPodService pods;
    @Autowired ScenarioSupport scenarios;
    @Autowired Clock clock;

    private static TrackingDto.EventRequest event(TrackingEventCode code) {
        return new TrackingDto.EventRequest(code, null, null, null, null, "APM11879");
    }

    private static TrackingDto.EventRequest event(TrackingEventCode code, LocalDateTime at) {
        return new TrackingDto.EventRequest(code, at, null, null, null, "APM11879");
    }

    // ------------------------------------------------------------------ cross-reference lookup

    @Test
    void anyOfTheFourIdentifiersResolvesTheSameConsignment() {
        String cfr = scenarios.inTransit();
        Consignment c = consignments.get(cfr);
        String enquiryNo = c.getEnquiry().getEnquiryNo();
        Long gcnSerial = scenarios.gcnSerialNoOf(cfr);
        String digital = scenarios.digitalGcNoOf(cfr);

        TrackingDto.LookupResponse byEnquiry = tracking.lookup(enquiryNo);
        TrackingDto.LookupResponse byCfr = tracking.lookup(cfr);
        TrackingDto.LookupResponse bySerial = tracking.lookup(String.valueOf(gcnSerial));
        TrackingDto.LookupResponse byDigital = tracking.lookup("  " + digital.toLowerCase() + " ");

        assertThat(byEnquiry.matchedBy()).isEqualTo(ReferenceType.ENQUIRY_NO);
        assertThat(byCfr.matchedBy()).isEqualTo(ReferenceType.CFR_NO);
        assertThat(bySerial.matchedBy()).isEqualTo(ReferenceType.GCN_SERIAL_NO);
        assertThat(byDigital.matchedBy()).isEqualTo(ReferenceType.DIGITAL_GC_NO);
        for (TrackingDto.LookupResponse r : List.of(byEnquiry, byCfr, bySerial, byDigital)) {
            assertThat(r.identifiers()).isEqualTo(new TrackingDto.Identifiers(enquiryNo, cfr, gcnSerial, digital));
            assertThat(r.shipment().status()).isEqualTo(ConsignmentStatus.IN_TRANSIT);
            assertThat(r.receipts()).hasSize(2);
        }
    }

    @Test
    void unknownReferencesAreNotFound() {
        for (String reference : List.of("NOPE-1", "EQUNKNOWN", "CFRUNKNOWN", "9999999999999", "GCUNKNOWN", "")) {
            assertThrows(ResourceNotFoundException.class, () -> tracking.lookup(reference));
        }
    }

    @Test
    void anEnquiryWithoutAConsignmentHasNothingToTrack() {
        String enquiryNo = scenarios.enquiryOnly();
        assertThrows(ResourceNotFoundException.class, () -> tracking.lookup(enquiryNo));
    }

    @Test
    void aQuotedConsignmentHasNoPhysicalOrDigitalIdentifierYet() {
        Consignment c = scenarios.quoted();
        TrackingDto.LookupResponse r = tracking.lookup(c.getCfrNo());
        assertThat(r.identifiers().gcnSerialNo()).isNull();
        assertThat(r.identifiers().digitalGcNo()).isNull();
        assertThat(r.shipment().arrivedAtDestinationHubAt()).isNull();
        assertThat(r.shipment().demurrageFreeUntil()).isNull();
        assertThat(r.shipment().deliveredAt()).isNull();
        assertThat(r.shipment().vehicleClaimDeadline()).isNull();
        assertThat(r.shipment().householdClaimDeadline()).isNull();
        assertThat(r.shipment().amountPaid()).isEqualByComparingTo("0");
        assertThat(r.shipment().balanceDue()).isEqualByComparingTo("7000");
        assertThat(r.receipts()).isEmpty();
        assertThat(r.coordinators()).isEmpty();
        assertThat(r.timeline().currentMilestone()).isNull();
        assertThat(r.timeline().digitalGcNo()).isNull();
    }

    @Test
    void aDeliveredConsignmentShowsDemurrageAndClaimDeadlines() {
        String cfr = scenarios.delivered(4);
        TrackingDto.ShipmentSummary s = tracking.lookup(cfr).shipment();
        assertThat(s.status()).isEqualTo(ConsignmentStatus.DELIVERED);
        assertThat(s.demurrageFreeUntil()).isEqualTo(s.arrivedAtDestinationHubAt().toLocalDate().plusDays(1));
        assertThat(s.demurrageDays()).isEqualTo(3);
        assertThat(s.demurrageAmount()).isEqualByComparingTo("30.00");
        assertThat(s.vehicleClaimDeadline()).isEqualTo(s.deliveredAt().plusHours(12));
        assertThat(s.householdClaimDeadline()).isEqualTo(s.deliveredAt().plusHours(72));
        assertThat(s.balanceDue()).isEqualByComparingTo("0");

        TrackingDto.Timeline timeline = tracking.lookup(cfr).timeline();
        assertThat(timeline.currentMilestone()).isEqualTo(TrackingMilestone.DELIVERED);
        assertThat(timeline.events()).extracting(TrackingDto.EventResponse::eventCode)
                .containsExactly(TrackingEventCode.ARRIVED_DESTINATION_HUB, TrackingEventCode.DELIVERED);
        assertThat(timeline.milestones()).allMatch(TrackingDto.MilestoneProgress::reached);
    }

    // ------------------------------------------------------------------ timeline

    @Test
    void aFreshlyDispatchedShipmentHasNoProgressYet() {
        TrackingDto.Timeline t = tracking.timeline(scenarios.digitalGcNoOf(scenarios.inTransit()));
        assertThat(t.currentMilestone()).isNull();
        assertThat(t.events()).isEmpty();
        assertThat(t.milestones()).hasSize(5).noneMatch(TrackingDto.MilestoneProgress::reached)
                .noneMatch(TrackingDto.MilestoneProgress::current);
    }

    @Test
    void theProgressBarFillsUpToTheLatestMilestone() {
        String digital = scenarios.digitalGcNoOf(scenarios.inTransit());
        tracking.record(digital, event(TrackingEventCode.BOOKED_AT_ORIGIN));
        TrackingDto.Timeline atRoute = tracking.timeline(" " + digital.toLowerCase());
        assertThat(atRoute.currentMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT_ROUTE);
        assertThat(atRoute.milestones()).extracting(TrackingDto.MilestoneProgress::reached)
                .containsExactly(true, true, false, false, false);
        assertThat(atRoute.milestones().get(1).current()).isTrue();
        assertThat(atRoute.milestones().get(1).reachedAt()).isNotNull();
        assertThat(atRoute.milestones().get(0).reachedAt()).isNull();

        tracking.record(digital, event(TrackingEventCode.IN_TRANSIT_LINE_HAUL));
        TrackingDto.Timeline lineHaul = tracking.timeline(digital);
        assertThat(lineHaul.currentMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT);
        assertThat(lineHaul.events()).extracting(TrackingDto.EventResponse::eventCode)
                .containsExactly(TrackingEventCode.BOOKED_AT_ORIGIN, TrackingEventCode.IN_TRANSIT_LINE_HAUL);
    }

    @Test
    void timelineOfAnUnknownDigitalGcNumberIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> tracking.timeline("GCKOL260000000"));
    }

    // ------------------------------------------------------------------ event ingestion rules

    @Test
    void scansDefaultTheTimestampHubAndDescription() {
        String digital = scenarios.digitalGcNoOf(scenarios.inTransit());
        TrackingEvent e = tracking.record(digital, event(TrackingEventCode.ARRIVED_ORIGIN_HUB));
        assertThat(e.getHubCity()).isEqualTo("KOLKATA HUB");
        assertThat(e.getEventDescription()).isEqualTo("Arrived at KOLKATA HUB");
        assertThat(e.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(e.getDigitalGcNo()).isEqualTo(digital);
        assertThat(e.getRecordedByAgentId()).isEqualTo("APM11879");
        assertThat(e.getEventTimestamp()).isBefore(LocalDateTime.now(clock).plusMinutes(1));
    }

    @Test
    void repeatedCodesAreAllowedButGoingBackwardsIsNot() {
        String digital = scenarios.digitalGcNoOf(scenarios.inTransit());
        tracking.record(digital, event(TrackingEventCode.IN_TRANSIT_LINE_HAUL));
        tracking.record(digital, event(TrackingEventCode.IN_TRANSIT_LINE_HAUL));
        assertThat(tracking.timeline(digital).events()).hasSize(2);
        TrackingDto.EventRequest backwards = event(TrackingEventCode.BOOKED_AT_ORIGIN);
        assertThrows(BusinessRuleException.class, () -> tracking.record(digital, backwards));
    }

    @Test
    void scansMustNotBeDatedInTheFuture() {
        String digital = scenarios.digitalGcNoOf(scenarios.inTransit());
        TrackingDto.EventRequest future = event(TrackingEventCode.BOOKED_AT_ORIGIN, LocalDateTime.now(clock).plusDays(1));
        assertThrows(BusinessRuleException.class, () -> tracking.record(digital, future));
    }

    @Test
    void scansForAnUnknownDigitalGcNumberAreNotFound() {
        TrackingDto.EventRequest request = event(TrackingEventCode.BOOKED_AT_ORIGIN);
        assertThrows(ResourceNotFoundException.class, () -> tracking.record("GCKOL260000000", request));
    }

    @Test
    void scansAreRejectedOnceTheConsignmentIsDelivered() {
        String digital = scenarios.digitalGcNoOf(scenarios.delivered(0));
        TrackingDto.EventRequest request = event(TrackingEventCode.OUT_FOR_DELIVERY);
        assertThrows(BusinessRuleException.class, () -> tracking.record(digital, request));
    }

    // ------------------------------------------------------------------ side effects

    @Test
    void arrivalAtTheDestinationHubStartsTheDemurrageClock() {
        String cfr = scenarios.inTransit();
        String digital = scenarios.digitalGcNoOf(cfr);
        LocalDateTime arrived = LocalDateTime.now(clock).minusDays(4);

        TrackingEvent e = tracking.record(digital, event(TrackingEventCode.ARRIVED_DESTINATION_HUB, arrived));

        assertThat(e.getEventCode()).isEqualTo(TrackingEventCode.ARRIVED_DESTINATION_HUB);
        assertThat(e.getHubCity()).isEqualTo("HYDERABAD HUB");
        Consignment c = consignments.get(cfr);
        assertThat(c.getArrivedAtDestinationHubAt()).isNotNull();
        assertThat(c.getArrivedAtDestinationHubAt().toLocalDate()).isEqualTo(arrived.toLocalDate());
        TrackingDto.EventRequest second = event(TrackingEventCode.ARRIVED_DESTINATION_HUB);
        assertThrows(BusinessRuleException.class, () -> tracking.record(digital, second));
    }

    @Test
    void deliveredScanNeedsAPodAndThenOpensTheClaimWindow() {
        String cfr = scenarios.inTransit();
        String digital = scenarios.digitalGcNoOf(cfr);
        tracking.record(digital, event(TrackingEventCode.ARRIVED_DESTINATION_HUB));
        TrackingDto.EventRequest delivered = event(TrackingEventCode.DELIVERED);

        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> tracking.record(digital, delivered));
        assertThat(ex.getViolations()).containsExactly("A delivery POD record is required before delivery");
        assertThat(consignments.get(cfr).getStatus()).isEqualTo(ConsignmentStatus.IN_TRANSIT);

        pods.record(cfr, TestData.completePod());
        TrackingEvent e = tracking.record(digital, delivered);

        assertThat(e.getEventCode()).isEqualTo(TrackingEventCode.DELIVERED);
        assertThat(e.getMilestone()).isEqualTo(TrackingMilestone.DELIVERED);
        Consignment c = consignments.get(cfr);
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.DELIVERED);
        assertThat(c.getDeliveredAt()).isNotNull();
        assertThat(tracking.lookup(cfr).shipment().vehicleClaimDeadline()).isEqualTo(c.getDeliveredAt().plusHours(12));
    }
}
