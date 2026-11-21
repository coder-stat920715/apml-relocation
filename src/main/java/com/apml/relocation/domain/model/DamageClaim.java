package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.DamageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "damage_claim")
@Getter
@Setter
@NoArgsConstructor
public class DamageClaim extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "survey_item_id")
    private SurveyItem surveyItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private DamageType damageType;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(nullable = false)
    private LocalDateTime lodgedAt;

    /** Count of PRE_PACKING / PACKED_AT_GODOWN photos on file for this item (or whole consignment). */
    private Integer baselineEvidenceCount;
    /** Count of UNPACKED_AT_DELIVERY photos on file for this item (or whole consignment). */
    private Integer deliveryEvidenceCount;
}
