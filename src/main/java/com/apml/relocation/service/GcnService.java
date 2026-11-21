package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.LiabilitySection;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GcnService {

    private static final long FIRST_SERIAL_NO = 1354001L;
    private static final Pattern TRUCK_NO = Pattern.compile("^[A-Z]{2}\\d{1,2}[A-Z]{0,3}\\d{4}$");
    private static final Set<ConsignmentStatus> ISSUABLE =
            Set.of(ConsignmentStatus.BOOKING_CONFIRMED, ConsignmentStatus.DOCUMENTS_VERIFIED, ConsignmentStatus.READY_FOR_LOADING);

    private final GoodsConsignmentNoteRepository gcns;
    private final ConsignmentRepository consignments;
    private final PaymentService payments;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean exists(String cfrNo) {
        return gcns.findByConsignment(load(cfrNo)).isPresent();
    }

    @Transactional(readOnly = true)
    public GoodsConsignmentNote get(String cfrNo) {
        return gcns.findByConsignment(load(cfrNo)).orElseThrow(() -> new ResourceNotFoundException("Goods Consignment Note for", cfrNo));
    }

    /** Issues the GCN on first call; later calls update the supplied fields (until dispatch). */
    @Transactional
    public GoodsConsignmentNote issue(String cfrNo, GcnDto.IssueRequest r) {
        Consignment c = load(cfrNo);
        if (!ISSUABLE.contains(c.getStatus())) {
            throw new BusinessRuleException("A Goods Consignment Note can only be issued or changed after booking confirmation "
                    + "and before dispatch (current status " + c.getStatus() + ")");
        }
        if (r.cfrReferenceNo() != null && !r.cfrReferenceNo().isBlank()
                && !r.cfrReferenceNo().trim().equalsIgnoreCase(c.getCfrNo())) {
            throw new BusinessRuleException("cfrReferenceNo " + r.cfrReferenceNo().trim()
                    + " does not match consignment CFR " + c.getCfrNo());
        }

        GoodsConsignmentNote g = gcns.findByConsignment(c).orElse(null);
        if (g == null) {
            g = new GoodsConsignmentNote();
            g.setConsignment(c);
            g.setGcnSerialNo(nextSerial(r.gcnSerialNo()));
            g.setGcnDate(LocalDateTime.now(clock));
            g.setPaymentBillingType(PaymentBillingType.TBB);
        } else if (r.gcnSerialNo() != null && !r.gcnSerialNo().equals(g.getGcnSerialNo())) {
            throw new BusinessRuleException("GCN serial number cannot be changed once issued");
        }
        g.setCfrReferenceNo(c.getCfrNo());

        if (r.ibaCode() != null) g.setIbaCode(r.ibaCode());
        if (r.carriageActRegNo() != null) g.setCarriageActRegNo(r.carriageActRegNo());
        if (r.gstin() != null) g.setGstin(r.gstin());
        if (r.sacCode() != null) g.setSacCode(r.sacCode());
        if (r.deliveryMode() != null) g.setDeliveryMode(r.deliveryMode());
        if (r.unloadingBy() != null) g.setUnloadingBy(r.unloadingBy());
        if (r.paymentBillingType() != null) g.setPaymentBillingType(r.paymentBillingType());
        if (r.totalPackages() != null) g.setTotalPackages(r.totalPackages());
        else if (g.getTotalPackages() == null) g.setTotalPackages(c.getEnquiry().getItems().stream().mapToInt(SurveyItem::getQuantity).sum());
        if (r.actualWeightKg() != null) g.setActualWeightKg(r.actualWeightKg());
        if (r.chargedWeightKg() != null) g.setChargedWeightKg(r.chargedWeightKg());
        if (r.lengthFt() != null) g.setLengthFt(r.lengthFt());
        if (r.breadthFt() != null) g.setBreadthFt(r.breadthFt());
        if (r.heightFt() != null) g.setHeightFt(r.heightFt());
        if (g.getActualWeightKg() != null && g.getChargedWeightKg() != null
                && g.getChargedWeightKg().compareTo(g.getActualWeightKg()) < 0) {
            throw new BusinessRuleException("Charged weight cannot be less than actual weight");
        }
        if (r.packingMaterialsUsed() != null) {
            g.getPackingMaterialsUsed().clear();
            g.getPackingMaterialsUsed().putAll(r.packingMaterialsUsed());
        }
        applyLiability(c, g, r.liabilitySection(), r.declaredGoodsValue());

        g = gcns.save(g);
        payments.syncBillingType(c);
        log.info("GCN {} issued/updated for {} (liability {})", g.getGcnSerialNo(), cfrNo, g.getLiabilitySection());
        return g;
    }

    /** Applies truck / seal / cube details at dispatch time; the GCN must already exist. */
    @Transactional
    public GoodsConsignmentNote applyDispatchDetails(Consignment c, GcnDto.DispatchRequest d) {
        GoodsConsignmentNote g = gcns.findByConsignment(c).orElseThrow(() -> new ComplianceViolationException(
                List.of("A Goods Consignment Note must be issued before dispatch")));
        if (d != null) {
            if (d.truckVehicleNo() != null && !d.truckVehicleNo().isBlank()) {
                String truck = d.truckVehicleNo().replaceAll("[\\s-]", "").toUpperCase();
                if (!TRUCK_NO.matcher(truck).matches()) {
                    throw new BusinessRuleException("Invalid truck registration number: " + d.truckVehicleNo());
                }
                g.setTruckVehicleNo(truck);
            }
            if (d.sealNo() != null && !d.sealNo().isBlank()) g.setSealNo(d.sealNo().trim());
            if (d.cubeNo() != null && !d.cubeNo().isBlank()) g.setCubeNo(d.cubeNo().trim());
        }
        return gcns.save(g);
    }

    /** Pre-dispatch gate: every mandatory GCN field must be populated and consistent. */
    @Transactional(readOnly = true)
    public void assertDispatchReady(Consignment c, GoodsConsignmentNote g) {
        List<String> v = new ArrayList<>();
        if (!c.getCfrNo().equals(g.getCfrReferenceNo())) v.add("GCN cfrReferenceNo does not match consignment CFR");
        if (g.getTruckVehicleNo() == null || g.getTruckVehicleNo().isBlank()) v.add("truckVehicleNo is required");
        if (g.getSealNo() == null || g.getSealNo().isBlank()) v.add("sealNo is required");
        if (c.getEnquiry().getMovementType() == MovementType.CUBES && (g.getCubeNo() == null || g.getCubeNo().isBlank())) {
            v.add("cubeNo is required for CUBES shipments");
        }
        if (g.getDeliveryMode() == null) v.add("deliveryMode (DOOR_DELIVERY / GODOWN_DELIVERY) must be selected");
        if (g.getLiabilitySection() == null) {
            v.add("liabilitySection is required");
        } else if (isInsured(c) && g.getLiabilitySection() != LiabilitySection.SECTION_11_INSURED_RISK) {
            v.add("Consignment has FOV cover, so liabilitySection must be SECTION_11_INSURED_RISK");
        } else if (!isInsured(c) && g.getLiabilitySection() == LiabilitySection.SECTION_11_INSURED_RISK) {
            v.add("SECTION_11_INSURED_RISK requires FOV / Transit Risk Coverage on the consignment");
        }
        if (g.getLiabilitySection() == LiabilitySection.SECTION_11_INSURED_RISK
                && (g.getDeclaredGoodsValue() == null || g.getDeclaredGoodsValue().signum() <= 0)) {
            v.add("declaredGoodsValue is required under Section 11");
        }
        if (g.getPaymentBillingType() != PaymentBillingType.PAID) v.add("paymentBillingType must be PAID");
        if (!v.isEmpty()) throw new ComplianceViolationException(v);
    }

    @Transactional
    public void markDispatched(GoodsConsignmentNote g) {
        g.setDispatchedAt(LocalDateTime.now(clock));
        gcns.save(g);
    }

    @Transactional(readOnly = true)
    public GoodsConsignmentNote find(Consignment c) {
        return gcns.findByConsignment(c).orElse(null);
    }

    // ------------------------------------------------------------------ helpers

    /** FOV / transit risk cover exists when the customer did not choose owner's risk and surveyed cover is above zero. */
    static boolean isInsured(Consignment c) {
        return !c.isOwnerRisk() && c.getEnquiry().getItems().stream()
                .anyMatch(i -> i.getRiskCoverageAvailed() != null && i.getRiskCoverageAvailed().signum() > 0);
    }

    private void applyLiability(Consignment c, GoodsConsignmentNote g, LiabilitySection requested, BigDecimal declared) {
        if (isInsured(c)) {
            if (requested != null && requested != LiabilitySection.SECTION_11_INSURED_RISK) {
                throw new ComplianceViolationException(List.of(
                        "Consignment has FOV / Transit Risk Coverage; liabilitySection must be SECTION_11_INSURED_RISK"));
            }
            g.setLiabilitySection(LiabilitySection.SECTION_11_INSURED_RISK);
            BigDecimal value = declared != null ? declared : g.getDeclaredGoodsValue();
            if (value == null) {
                value = c.getEnquiry().getItems().stream().map(SurveyItem::getDeclaredValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (value.signum() <= 0) {
                throw new ComplianceViolationException(List.of("declaredGoodsValue must be provided under Section 11"));
            }
            g.setDeclaredGoodsValue(value);
        } else {
            if (requested == LiabilitySection.SECTION_11_INSURED_RISK) {
                throw new ComplianceViolationException(List.of(
                        "SECTION_11_INSURED_RISK requires FOV / Transit Risk Coverage on the consignment"));
            }
            g.setLiabilitySection(LiabilitySection.SECTION_10_LIMITED_LIABILITY);
            if (declared != null) g.setDeclaredGoodsValue(declared);
        }
    }

    private long nextSerial(Long requested) {
        if (requested != null) {
            if (gcns.existsByGcnSerialNo(requested)) throw new DuplicateResourceException("GCN serial number already used: " + requested);
            return requested;
        }
        return gcns.findTopByOrderByGcnSerialNoDesc().map(x -> x.getGcnSerialNo() + 1).orElse(FIRST_SERIAL_NO);
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
