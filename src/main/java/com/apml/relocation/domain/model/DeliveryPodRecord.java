package com.apml.relocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proof-of-delivery handover checklist. Raw ID numbers are never stored: only a verification flag
 * and a masked reference (XXXX-XXXX-1234).
 */
@Entity
@Table(name = "delivery_pod_record")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPodRecord extends AuditableEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "consignment_id", unique = true)
    private Consignment consignment;

    private boolean originalRcVerified;
    private boolean originalInsuranceVerified;
    private boolean originalPucVerified;
    private boolean consigneeIdVerified;
    @Column(length = 20)
    private String consigneeIdMaskedRef;
    private boolean paymentReceiptsVerified;
    private boolean unpackedAndInspectedBeforeSignOff;

    private String podSignedBy;
    private LocalDateTime podSignedAt;
    @Column(length = 1000)
    private String deliveryRemarks;
    private boolean damageNotedOnPod;
}
