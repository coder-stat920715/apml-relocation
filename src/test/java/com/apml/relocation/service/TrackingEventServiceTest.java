package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.repository.TrackingEventRepository;
import com.apml.relocation.support.TestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingEventServiceTest {

    @Mock TrackingEventRepository events;
    @Mock GoodsConsignmentNoteRepository gcns;

    private TrackingEventService service;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new TrackingEventService(events, gcns);
        consignment = TestData.consignment(ConsignmentStatus.IN_TRANSIT,
                TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000")));
        when(events.save(any(TrackingEvent.class))).thenAnswer(i -> i.getArgument(0));
    }

    private GoodsConsignmentNote gcn() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setDigitalGcNo("GCKOL262708112");
        g.setTruckVehicleNo("WB03D1234");
        when(gcns.findByConsignment(consignment)).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    void defaultsComeFromTheEventCodeWhenNoGcnAndNoDetailsExist() {
        TrackingEvent e = service.append(consignment, TrackingEventCode.ARRIVED_ORIGIN_HUB, TestData.NOW, null);
        assertThat(e.getDigitalGcNo()).isNull();
        assertThat(e.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT_ROUTE);
        assertThat(e.getEventCode()).isEqualTo(TrackingEventCode.ARRIVED_ORIGIN_HUB);
        assertThat(e.getEventTimestamp()).isEqualTo(TestData.NOW);
        assertThat(e.getEventDate()).isEqualTo(TestData.NOW.toLocalDate());
        assertThat(e.getHubCity()).isEqualTo("KOLKATA HUB");
        assertThat(e.getEventDescription()).isEqualTo("Arrived at KOLKATA HUB");
        assertThat(e.getTruckVehicleNo()).isNull();
        assertThat(e.getRecordedByAgentId()).isNull();
    }

    @Test
    void gcnSuppliesDigitalNumberAndDefaultTruck() {
        gcn();
        TrackingEvent e = service.append(consignment, TrackingEventCode.IN_TRANSIT_LINE_HAUL, TestData.NOW,
                new TrackingEventService.Details("  ", "", " ", "APM11879"));
        assertThat(e.getDigitalGcNo()).isEqualTo("GCKOL262708112");
        assertThat(e.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(e.getHubCity()).isEqualTo("KOLKATA HUB");
        assertThat(e.getEventDescription()).isEqualTo("On the way KOLKATA HUB To HYDERABAD HUB");
        assertThat(e.getRecordedByAgentId()).isEqualTo("APM11879");
    }

    @Test
    void explicitDetailsOverrideTheDefaults() {
        gcn();
        TrackingEvent e = service.append(consignment, TrackingEventCode.BOOKED_AT_ORIGIN, TestData.NOW,
                new TrackingEventService.Details("KOLKATA", "Consignment Booked at KOLKATTA", "OD02X9999", "APM1"));
        assertThat(e.getHubCity()).isEqualTo("KOLKATA");
        assertThat(e.getEventDescription()).isEqualTo("Consignment Booked at KOLKATTA");
        assertThat(e.getTruckVehicleNo()).isEqualTo("OD02X9999");
    }

    @Test
    void timelineAndLatestDelegateToTheRepository() {
        TrackingEvent e = new TrackingEvent();
        when(events.findByConsignmentOrderByEventTimestampAscIdAsc(consignment)).thenReturn(List.of(e));
        when(events.findTopByConsignmentOrderByIdDesc(consignment)).thenReturn(e);
        assertThat(service.timeline(consignment)).containsExactly(e);
        assertThat(service.latest(consignment)).isSameAs(e);
        assertThat(TrackingEventService.Details.EMPTY.hubCity()).isNull();
    }
}
