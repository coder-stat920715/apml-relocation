package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.LiabilitySection;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.UnloadingBy;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Goods Consignment Note / Lorry Receipt, issued after packing at the godown (separate from the CFR quotation). */
@Entity
@Table(name = "goods_consignment_note")
@Getter
@Setter
@NoArgsConstructor
public class GoodsConsignmentNote extends AuditableEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "consignment_id", unique = true)
    private Consignment consignment;

    // ---- header & legal identifiers
    @Column(nullable = false, unique = true)
    private Long gcnSerialNo;
    @Column(nullable = false)
    private LocalDateTime gcnDate;
    private String ibaCode = "MUA-1783";
    private String carriageActRegNo = "MH 01/CRA/2016/31";
    private String gstin = "19AAFCA3559A1ZW";
    private String sacCode = "996511";
    @Column(nullable = false, length = 30)
    private String cfrReferenceNo;

    // ---- liability & delivery flags
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LiabilitySection liabilitySection;
    @Column(precision = 14, scale = 2)
    private BigDecimal declaredGoodsValue;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliveryMode deliveryMode;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnloadingBy unloadingBy;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PaymentBillingType paymentBillingType;

    // ---- packages, weight, dimensions
    private Integer totalPackages;
    @Column(precision = 10, scale = 2)
    private BigDecimal actualWeightKg;
    @Column(precision = 10, scale = 2)
    private BigDecimal chargedWeightKg;
    @Column(precision = 8, scale = 2)
    private BigDecimal lengthFt;
    @Column(precision = 8, scale = 2)
    private BigDecimal breadthFt;
    @Column(precision = 8, scale = 2)
    private BigDecimal heightFt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gcn_packing_material", joinColumns = @JoinColumn(name = "gcn_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "material", length = 20)
    @Column(name = "quantity")
    private Map<PackingMaterial, Integer> packingMaterialsUsed = new HashMap<>();

    // ---- line haul
    private String truckVehicleNo;
    private String cubeNo;
    private String sealNo;
    private LocalDateTime dispatchedAt;
}
