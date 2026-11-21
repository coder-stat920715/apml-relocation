package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.exception.ClaimRejectedException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.DamageClaimRepository;
import com.apml.relocation.repository.InspectionMediaRepository;
import com.apml.relocation.repository.MasterArticleDeclarationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService {

    private final ConsignmentRepository consignments;
    private final MasterArticleDeclarationRepository declarations;
    private final DamageClaimRepository claims;
    private final InspectionMediaRepository media;
    private final Clock clock;

    /**
     * Claim rules:
     *  - owner's-risk (no FOV) shipments are rejected;
     *  - vehicles: within 12h of delivery, exterior damage only; household goods / cartons: within 72h;
     *  - payout is capped at the cover declared for the item on the Master Article List (omitted items = 0).
     * Pre-packing / packed / delivery photos on file are counted on the claim as adjudication evidence.
     */
    @Transactional
    public DamageClaim lodge(String cfrNo, Long surveyItemId, DamageType damageType, String description, BigDecimal claimed) {
        Consignment c = consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));

        if (c.isOwnerRisk()) {
            throw new ClaimRejectedException("Transit Risk Coverage (FOV) was not opted for; the shipment travelled at owner's risk");
        }
        if (c.getDeliveredAt() == null) {
            throw new ClaimRejectedException("Claims can only be lodged after the consignment has been delivered");
        }
        SurveyItem item = c.getEnquiry().getItems().stream()
                .filter(i -> i.getId().equals(surveyItemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Survey item", String.valueOf(surveyItemId)));

        ItemCategory category = item.getCategory();
        LocalDateTime now = LocalDateTime.now(clock);
        Duration elapsed = Duration.between(c.getDeliveredAt(), now);
        if (elapsed.compareTo(category.getClaimWindow()) > 0) {
            throw new ClaimRejectedException("Claim window of " + category.getClaimWindow().toHours()
                    + " hours from delivery has expired for " + category);
        }
        if (category.isVehicle() && damageType != DamageType.EXTERIOR) {
            throw new ClaimRejectedException("Only exterior damage is eligible for vehicles; internal/mechanical faults are excluded");
        }

        MasterArticleDeclaration decl = declarations.findByConsignment(c)
                .orElseThrow(() -> new ClaimRejectedException("No signed Master Article List on file; item is ineligible for claims"));
        BigDecimal cover = decl.coverageFor(surveyItemId);
        if (cover.signum() <= 0) {
            throw new ClaimRejectedException("Item has no declared value / risk coverage on the Master Article List");
        }

        List<InspectionMedia> evidence = media.findByConsignmentOrderByCapturedAtAsc(c).stream()
                .filter(m -> m.getSurveyItem() == null || surveyItemId.equals(m.getSurveyItem().getId()))
                .toList();
        int baseline = (int) evidence.stream()
                .filter(m -> m.getStage() == MediaStage.PRE_PACKING || m.getStage() == MediaStage.PACKED_AT_GODOWN).count();
        int atDelivery = (int) evidence.stream().filter(m -> m.getStage() == MediaStage.UNPACKED_AT_DELIVERY).count();

        BigDecimal approved = claimed.min(cover);
        DamageClaim claim = new DamageClaim();
        claim.setConsignment(c);
        claim.setSurveyItem(item);
        claim.setDamageType(damageType);
        claim.setDescription(description);
        claim.setClaimedAmount(claimed);
        claim.setApprovedAmount(approved);
        claim.setStatus(claimed.compareTo(cover) > 0 ? ClaimStatus.APPROVED_CAPPED : ClaimStatus.APPROVED);
        claim.setLodgedAt(now);
        claim.setBaselineEvidenceCount(baseline);
        claim.setDeliveryEvidenceCount(atDelivery);
        log.info("Claim for CFR {} item {} approved INR {} (claimed {}, cover {}, evidence {}/{})",
                cfrNo, surveyItemId, approved, claimed, cover, baseline, atDelivery);
        return claims.save(claim);
    }

    @Transactional(readOnly = true)
    public List<DamageClaim> list(String cfrNo) {
        Consignment c = consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
        return claims.findByConsignment(c);
    }
}
