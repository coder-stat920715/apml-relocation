package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.MovementType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "enquiry")
@Getter
@Setter
@NoArgsConstructor
public class Enquiry extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String enquiryNo;

    @Column(nullable = false)
    private String customerName;
    @Column(nullable = false, length = 15)
    private String mobile;
    private String email;
    private String gstNo;
    private boolean gstExempted;

    @Column(nullable = false)
    private String originCity;
    @Column(nullable = false)
    private String destinationCity;
    @Column(length = 500)
    private String pickupAddress;
    @Column(length = 500)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType movementType;

    private int distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryStatus status = EnquiryStatus.SUBMITTED;

    @Column(nullable = false)
    private LocalDate submittedOn;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SurveyItem> items = new ArrayList<>();

    public void addItem(SurveyItem item) {
        item.setEnquiry(this);
        items.add(item);
    }

    /** Total CFT is always derived from survey items (volume per unit x quantity). */
    public BigDecimal getTotalCft() {
        return items.stream()
                .map(i -> i.getVolumeCft().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasCategory(com.apml.relocation.domain.enums.ItemCategory category) {
        return items.stream().anyMatch(i -> i.getCategory() == category);
    }

    public LocalDate getValidUntil() { return submittedOn.plusDays(15); }

    public LocalDate getMoveDeadline() { return submittedOn.plusDays(30); }
}
