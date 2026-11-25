package com.apml.relocation.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.CoordinatorRole;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.CoordinatorContact;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.service.CoordinatorService;
import com.apml.relocation.service.TrackingEventService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class DataSeederTest {

    @Autowired DataSeeder seeder;
    @Autowired ConsignmentRepository consignments;
    @Autowired GoodsConsignmentNoteRepository gcns;
    @Autowired CoordinatorService coordinators;
    @Autowired TrackingEventService trackingEvents;

    @Test
    void sampleScenarioIsSeededAndInTransit() {
        Consignment c = consignments.findByCfrNo(DataSeeder.CFR_NO).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.IN_TRANSIT);
        assertThat(c.getTotalPayableAmount()).isEqualByComparingTo("7000.00");
        assertThat(c.getEnquiry().getEnquiryNo()).isEqualTo(DataSeeder.ENQUIRY_NO);
    }

    @Test
    void allFourIdentifiersAreLinked() {
        GoodsConsignmentNote g = gcns.findByDigitalGcNo(DataSeeder.DIGITAL_GC_NO).orElseThrow();
        assertThat(g.getGcnSerialNo()).isEqualTo(DataSeeder.GCN_SERIAL_NO);
        assertThat(g.getCfrReferenceNo()).isEqualTo(DataSeeder.CFR_NO);
        assertThat(g.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(g.getConsignment().getEnquiry().getEnquiryNo()).isEqualTo(DataSeeder.ENQUIRY_NO);
    }

    @Test
    void trackingHistoryMatchesThePortal() {
        Consignment c = consignments.findByCfrNo(DataSeeder.CFR_NO).orElseThrow();
        List<TrackingEvent> timeline = trackingEvents.timeline(c);
        assertThat(timeline).extracting(TrackingEvent::getEventCode).containsExactly(TrackingEventCode.BOOKED_AT_ORIGIN,
                TrackingEventCode.EN_ROUTE_ORIGIN_HUB, TrackingEventCode.ARRIVED_ORIGIN_HUB, TrackingEventCode.IN_TRANSIT_LINE_HAUL);
        assertThat(timeline).extracting(TrackingEvent::getMilestone).containsExactly(TrackingMilestone.IN_TRANSIT_ROUTE,
                TrackingMilestone.IN_TRANSIT_ROUTE, TrackingMilestone.IN_TRANSIT_ROUTE, TrackingMilestone.IN_TRANSIT);
        assertThat(timeline).extracting(TrackingEvent::getEventDescription).containsExactly(
                "Consignment Booked at KOLKATTA", "On the way KOLKATTA To KOLKATTA HUB", "Arrived at KOLKATTA HUB",
                "On the way KOLKATTA HUB To HYDERABAD HUB");
        assertThat(timeline).allSatisfy(e -> assertThat(e.getDigitalGcNo()).isEqualTo(DataSeeder.DIGITAL_GC_NO));
    }

    @Test
    void hubCoordinatorsAreSeeded() {
        List<CoordinatorContact> contacts = coordinators.list(DataSeeder.CFR_NO);
        assertThat(contacts).extracting(CoordinatorContact::getName)
                .containsExactlyInAnyOrder("APM11879", "Rahul Ji", "Ashok Sharma Ji");
        assertThat(contacts).extracting(CoordinatorContact::getRole).containsExactlyInAnyOrder(CoordinatorRole.BOOKING_AGENT,
                CoordinatorRole.ORIGIN_HUB_COORDINATOR, CoordinatorRole.ORIGIN_HUB_COORDINATOR);
    }

    @Test
    void runningTheSeederAgainIsANoOp() {
        long before = consignments.count();
        seeder.run();
        assertThat(consignments.count()).isEqualTo(before);
    }
}
