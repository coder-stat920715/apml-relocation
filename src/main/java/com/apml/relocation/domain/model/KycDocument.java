package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.KycType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sensitive: documentNumber must never be logged or returned unmasked (see MaskingUtil). */
@Entity
@Table(name = "kyc_document", uniqueConstraints = @UniqueConstraint(columnNames = {"consignment_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class KycDocument extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycType type;

    private String documentNumber;
    private Integer photoCount;
    private boolean verified;

    @Override
    public String toString() { return "KycDocument[type=" + type + ", verified=" + verified + "]"; }
}
