package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "consignment")
@Getter
@Setter
@NoArgsConstructor
public class Consignment extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String cfrNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "enquiry_id")
    private Enquiry enquiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ConsignmentStatus status = ConsignmentStatus.QUOTED;

    @Column(nullable = false)
    private LocalDate packingDate;
    @Column(nullable = false)
    private LocalDate loadingDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grossFreightAmount;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal fovChargeAmount;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gstAmount;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPayableAmount;

    /** true = customer did NOT opt for FOV (Transit Risk Coverage); claims are rejected. */
    private boolean ownerRisk;

    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    @Column(precision = 14, scale = 2)
    private BigDecimal cancellationCharge;
    @Column(precision = 14, scale = 2)
    private BigDecimal refundAmount;

    /** When the consignment reached the destination hub; starts the 1-day free demurrage period. */
    private LocalDateTime arrivedAtDestinationHubAt;
    private Integer demurrageDays;
    @Column(precision = 14, scale = 2)
    private BigDecimal demurrageAmount;
}
