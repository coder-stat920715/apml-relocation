package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transit_log")
@Getter
@Setter
@NoArgsConstructor
public class TransitLog extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Column(nullable = false)
    private String location;
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ConsignmentStatus statusAtLog;

    @Column(nullable = false)
    private LocalDateTime loggedAt;
}
