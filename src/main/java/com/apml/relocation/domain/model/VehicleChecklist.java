package com.apml.relocation.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicle_checklist")
@Getter
@Setter
@NoArgsConstructor
public class VehicleChecklist extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "survey_item_id")
    private SurveyItem surveyItem;

    private boolean rcBook;
    private boolean validInsurance;
    private boolean pucCertificate;
    private boolean roadTaxReceipt;

    /** Two-wheelers: must be true. */
    private Boolean fuelTankEmpty;

    /** Four-wheelers: must be <= 24.0 litres. */
    @Column(precision = 6, scale = 2)
    private BigDecimal fuelLiters;
}
