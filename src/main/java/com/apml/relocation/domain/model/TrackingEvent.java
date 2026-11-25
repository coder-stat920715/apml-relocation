package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.enums.TrackingMilestone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Append-only hub scan event; rows are never updated once written. */
@Entity
@Immutable
@Table(name = "tracking_event", indexes = @Index(name = "idx_tracking_event_consignment", columnList = "consignment_id"))
@Getter
@Setter
@NoArgsConstructor
public class TrackingEvent extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Column(name = "digital_gc_no", length = 20)
    private String digitalGcNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrackingMilestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrackingEventCode eventCode;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(length = 100)
    private String hubCity;

    @Column(length = 500)
    private String eventDescription;

    private String truckVehicleNo;
    private String recordedByAgentId;
}
