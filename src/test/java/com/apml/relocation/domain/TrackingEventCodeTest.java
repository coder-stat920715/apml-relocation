package com.apml.relocation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import org.junit.jupiter.api.Test;

class TrackingEventCodeTest {

    @Test
    void everyCodeBelongsToThePortalMilestone() {
        assertThat(TrackingEventCode.PICKUP_SCHEDULED.getMilestone()).isEqualTo(TrackingMilestone.PICK_UP_SCHEDULED);
        assertThat(TrackingEventCode.BOOKED_AT_ORIGIN.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT_ROUTE);
        assertThat(TrackingEventCode.EN_ROUTE_ORIGIN_HUB.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT_ROUTE);
        assertThat(TrackingEventCode.ARRIVED_ORIGIN_HUB.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT_ROUTE);
        assertThat(TrackingEventCode.IN_TRANSIT_LINE_HAUL.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT);
        assertThat(TrackingEventCode.ARRIVED_DESTINATION_HUB.getMilestone()).isEqualTo(TrackingMilestone.IN_TRANSIT);
        assertThat(TrackingEventCode.OUT_FOR_DELIVERY.getMilestone()).isEqualTo(TrackingMilestone.OUT_FOR_DELIVERY);
        assertThat(TrackingEventCode.DELIVERED.getMilestone()).isEqualTo(TrackingMilestone.DELIVERED);
    }

    @Test
    void hubCityIsOriginOrDestinationInUpperCase() {
        assertThat(TrackingEventCode.BOOKED_AT_ORIGIN.hubCity("Kolkata", "Hyderabad")).isEqualTo("KOLKATA");
        assertThat(TrackingEventCode.ARRIVED_ORIGIN_HUB.hubCity("Kolkata", "Hyderabad")).isEqualTo("KOLKATA HUB");
        assertThat(TrackingEventCode.ARRIVED_DESTINATION_HUB.hubCity("Kolkata", "Hyderabad")).isEqualTo("HYDERABAD HUB");
        assertThat(TrackingEventCode.DELIVERED.hubCity("Kolkata", "Hyderabad")).isEqualTo("HYDERABAD");
    }

    @Test
    void descriptionsFollowThePortalWording() {
        assertThat(TrackingEventCode.BOOKED_AT_ORIGIN.description("Kolkata", "Hyderabad")).isEqualTo("Consignment Booked at KOLKATA");
        assertThat(TrackingEventCode.EN_ROUTE_ORIGIN_HUB.description("Kolkata", "Hyderabad"))
                .isEqualTo("On the way KOLKATA To KOLKATA HUB");
        assertThat(TrackingEventCode.IN_TRANSIT_LINE_HAUL.description("Kolkata", "Hyderabad"))
                .isEqualTo("On the way KOLKATA HUB To HYDERABAD HUB");
        assertThat(TrackingEventCode.ARRIVED_DESTINATION_HUB.description("Kolkata", "Hyderabad")).isEqualTo("Arrived at HYDERABAD HUB");
        assertThat(TrackingEventCode.PICKUP_SCHEDULED.description("Kolkata", "Hyderabad")).isEqualTo("Pick up scheduled at KOLKATA");
        assertThat(TrackingEventCode.OUT_FOR_DELIVERY.description("Kolkata", "Hyderabad"))
                .isEqualTo("Out for delivery from HYDERABAD HUB");
        assertThat(TrackingEventCode.DELIVERED.description("Kolkata", "Hyderabad")).isEqualTo("Delivered at HYDERABAD");
        assertThat(TrackingEventCode.ARRIVED_ORIGIN_HUB.description("Kolkata", "Hyderabad")).isEqualTo("Arrived at KOLKATA HUB");
    }

    @Test
    void codesAreDeclaredInLifecycleOrder() {
        assertThat(TrackingEventCode.values()).extracting(Enum::name).containsExactly("PICKUP_SCHEDULED", "BOOKED_AT_ORIGIN",
                "EN_ROUTE_ORIGIN_HUB", "ARRIVED_ORIGIN_HUB", "IN_TRANSIT_LINE_HAUL", "ARRIVED_DESTINATION_HUB",
                "OUT_FOR_DELIVERY", "DELIVERED");
    }
}
