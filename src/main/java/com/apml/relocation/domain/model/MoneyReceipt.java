package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.PaymentMode;
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
@Table(name = "money_receipt")
@Getter
@Setter
@NoArgsConstructor
public class MoneyReceipt extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String receiptNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentMode mode;

    /** Cheque number or transaction reference, e.g. APMEB-5093071311. */
    private String chequeOrTxnRef;
    /** Issuing branch code, e.g. KOL. */
    private String branchCode;
    /** Agent who generated the receipt, e.g. APM11879. */
    private String generatedByAgentId;
    /** Only verified receipts count towards the amount paid. */
    private Boolean verified = Boolean.TRUE;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
}
