package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.CoordinatorRole;
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

/** Hub / field contact for one move; the phone number is only ever exposed masked. */
@Entity
@Table(name = "coordinator_contact")
@Getter
@Setter
@NoArgsConstructor
public class CoordinatorContact extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 35)
    private CoordinatorRole role;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String branchCode;

    @Column(length = 15)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    @Override
    public String toString() { return "CoordinatorContact[role=" + role + ", name=" + name + ", branch=" + branchCode + "]"; }
}
