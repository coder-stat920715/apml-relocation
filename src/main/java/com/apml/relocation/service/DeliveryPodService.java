package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DeliveryPodRecord;
import com.apml.relocation.dto.PodDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.DeliveryPodRecordRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryPodService {

    private static final String FULLY_MASKED = "XXXX-XXXX-XXXX";

    private final DeliveryPodRecordRepository pods;
    private final ConsignmentRepository consignments;
    private final Clock clock;

    /** Records (or replaces) the handover checklist while the consignment is at the destination, i.e. IN_TRANSIT. */
    @Transactional
    public DeliveryPodRecord record(String cfrNo, PodDto.Request request) {
        Consignment c = load(cfrNo);
        if (c.getStatus() != ConsignmentStatus.IN_TRANSIT) {
            throw new BusinessRuleException("A POD can only be recorded while the consignment is IN_TRANSIT");
        }
        return record(c, request);
    }

    @Transactional
    public DeliveryPodRecord record(Consignment c, PodDto.Request r) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime signedAt = r.podSignedAt() == null ? now : r.podSignedAt();
        if (signedAt.isAfter(now)) {
            throw new BusinessRuleException("podSignedAt cannot be in the future");
        }
        DeliveryPodRecord p = pods.findByConsignment(c).orElseGet(DeliveryPodRecord::new);
        p.setConsignment(c);
        p.setOriginalRcVerified(Boolean.TRUE.equals(r.originalRcVerified()));
        p.setOriginalInsuranceVerified(Boolean.TRUE.equals(r.originalInsuranceVerified()));
        p.setOriginalPucVerified(Boolean.TRUE.equals(r.originalPucVerified()));
        p.setConsigneeIdVerified(Boolean.TRUE.equals(r.consigneeIdVerified()));
        p.setConsigneeIdMaskedRef(maskReference(r.consigneeIdReference()));
        p.setPaymentReceiptsVerified(Boolean.TRUE.equals(r.paymentReceiptsVerified()));
        p.setUnpackedAndInspectedBeforeSignOff(Boolean.TRUE.equals(r.unpackedAndInspectedBeforeSignOff()));
        p.setPodSignedBy(r.podSignedBy());
        p.setPodSignedAt(signedAt);
        p.setDeliveryRemarks(r.deliveryRemarks());
        p.setDamageNotedOnPod(Boolean.TRUE.equals(r.damageNotedOnPod()));
        log.info("POD recorded for {} (signed by {}, damage noted: {})", c.getCfrNo(), p.getPodSignedBy(), p.isDamageNotedOnPod());
        return pods.save(p);
    }

    @Transactional(readOnly = true)
    public DeliveryPodRecord get(String cfrNo) {
        return pods.findByConsignment(load(cfrNo)).orElseThrow(() -> new ResourceNotFoundException("Delivery POD for", cfrNo));
    }

    /**
     * Delivery gate: a POD must exist and every handover check must be ticked. Original RC, insurance and PUC
     * only apply when the consignment carries a vehicle.
     */
    @Transactional(readOnly = true)
    public void assertDeliverable(Consignment c) {
        DeliveryPodRecord p = pods.findByConsignment(c).orElse(null);
        if (p == null) {
            throw new ComplianceViolationException(List.of("A delivery POD record is required before delivery"));
        }
        List<String> violations = new ArrayList<>();
        if (c.getEnquiry().getItems().stream().anyMatch(i -> i.getCategory().isVehicle())) {
            if (!p.isOriginalRcVerified()) violations.add("Original RC book was not verified at handover");
            if (!p.isOriginalInsuranceVerified()) violations.add("Original insurance was not verified at handover");
            if (!p.isOriginalPucVerified()) violations.add("Original PUC certificate was not verified at handover");
        }
        if (!p.isConsigneeIdVerified()) violations.add("Consignee ID was not verified");
        if (!p.isPaymentReceiptsVerified()) violations.add("Payment receipts were not verified before release");
        if (!p.isUnpackedAndInspectedBeforeSignOff()) violations.add("Goods were not unpacked and inspected before sign-off");
        if (p.getPodSignedBy() == null || p.getPodSignedBy().isBlank()) violations.add("POD must be signed by the consignee");
        if (!violations.isEmpty()) {
            throw new ComplianceViolationException(violations);
        }
    }

    /** XXXX-XXXX-1234 when at least four digits are supplied, otherwise fully masked. Never keeps the raw number. */
    static String maskReference(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= 4 ? "XXXX-XXXX-" + digits.substring(digits.length() - 4) : FULLY_MASKED;
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
