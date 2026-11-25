package com.apml.relocation.domain.enums;

/**
 * Hub scan events. Each code belongs to one portal milestone and knows how to describe itself
 * (%O = origin city, %D = destination city) and which hub it happens at.
 */
public enum TrackingEventCode {
    PICKUP_SCHEDULED(TrackingMilestone.PICK_UP_SCHEDULED, false, "", "Pick up scheduled at %O"),
    BOOKED_AT_ORIGIN(TrackingMilestone.IN_TRANSIT_ROUTE, false, "", "Consignment Booked at %O"),
    EN_ROUTE_ORIGIN_HUB(TrackingMilestone.IN_TRANSIT_ROUTE, false, "", "On the way %O To %O HUB"),
    ARRIVED_ORIGIN_HUB(TrackingMilestone.IN_TRANSIT_ROUTE, false, " HUB", "Arrived at %O HUB"),
    IN_TRANSIT_LINE_HAUL(TrackingMilestone.IN_TRANSIT, false, " HUB", "On the way %O HUB To %D HUB"),
    ARRIVED_DESTINATION_HUB(TrackingMilestone.IN_TRANSIT, true, " HUB", "Arrived at %D HUB"),
    OUT_FOR_DELIVERY(TrackingMilestone.OUT_FOR_DELIVERY, true, " HUB", "Out for delivery from %D HUB"),
    DELIVERED(TrackingMilestone.DELIVERED, true, "", "Delivered at %D");

    private final TrackingMilestone milestone;
    private final boolean atDestination;
    private final String hubSuffix;
    private final String template;

    TrackingEventCode(TrackingMilestone milestone, boolean atDestination, String hubSuffix, String template) {
        this.milestone = milestone;
        this.atDestination = atDestination;
        this.hubSuffix = hubSuffix;
        this.template = template;
    }

    public TrackingMilestone getMilestone() { return milestone; }

    public String hubCity(String origin, String destination) {
        return (atDestination ? destination : origin).toUpperCase() + hubSuffix;
    }

    public String description(String origin, String destination) {
        return template.replace("%O", origin.toUpperCase()).replace("%D", destination.toUpperCase());
    }
}
