package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.ItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_item")
@Getter
@Setter
@NoArgsConstructor
public class SurveyItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id")
    private Enquiry enquiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemCategory category;

    @Column(nullable = false)
    private String description;

    private int quantity = 1;

    /** Volume per unit in cubic feet. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal volumeCft;

    private boolean crateRequired;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredValue = BigDecimal.ZERO;

    /** Freight-on-Value (FOV) cover amount in INR requested at survey time. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal riskCoverageAvailed = BigDecimal.ZERO;
}
