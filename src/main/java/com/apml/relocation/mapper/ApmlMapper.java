package com.apml.relocation.mapper;

import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.KycDocument;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.MoneyReceipt;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.domain.model.VehicleChecklist;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.dto.AddressDto;
import com.apml.relocation.domain.model.CoordinatorContact;
import com.apml.relocation.domain.model.DeliveryPodRecord;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.dto.ComplianceDto;
import com.apml.relocation.dto.CoordinatorDto;
import com.apml.relocation.dto.PodDto;
import com.apml.relocation.dto.TrackingDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.PaymentDto;
import com.apml.relocation.dto.TransitDto;
import com.apml.relocation.util.MaskingUtil;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApmlMapper {
    private ApmlMapper() {}

    public static EnquiryDto.ItemResponse toResponse(SurveyItem i) {
        return new EnquiryDto.ItemResponse(i.getId(), i.getCategory(), i.getDescription(), i.getQuantity(),
                i.getVolumeCft(), i.isCrateRequired(), i.getDeclaredValue(), i.getRiskCoverageAvailed());
    }

    public static EnquiryDto.Response toResponse(Enquiry e) {
        return new EnquiryDto.Response(e.getEnquiryNo(), e.getStatus(), e.getCustomerName(), e.getMobile(), e.getEmail(),
                e.getGstNo(), e.isGstExempted(), e.getOriginCity(), e.getDestinationCity(), e.getPickupAddress(),
                e.getDeliveryAddress(), e.getMovementType(), e.getDistanceKm(), e.getTotalCft(), e.getSubmittedOn(),
                e.getValidUntil(), e.getMoveDeadline(), e.getItems().stream().map(ApmlMapper::toResponse).toList(),
                AddressDto.from(e.getConsignorAddress()), AddressDto.from(e.getConsigneeAddress()),
                e.getConsigneeName(), e.getConsigneeMobile());
    }

    public static ConsignmentDto.Response toResponse(Consignment c, BigDecimal paid, BigDecimal requiredAdvance) {
        return new ConsignmentDto.Response(c.getCfrNo(), c.getEnquiry().getEnquiryNo(), c.getStatus(), c.getPackingDate(),
                c.getLoadingDate(), c.getEnquiry().getTotalCft(), c.getGrossFreightAmount(), c.getFovChargeAmount(),
                c.getGstAmount(), c.getTotalPayableAmount(), paid, c.getTotalPayableAmount().subtract(paid),
                requiredAdvance, c.isOwnerRisk(), c.getDeliveredAt(), c.getCancelledAt(), c.getCancellationCharge(),
                c.getRefundAmount(), c.getArrivedAtDestinationHubAt(), c.getDemurrageDays(), c.getDemurrageAmount());
    }

    public static PaymentDto.Response toResponse(MoneyReceipt r) {
        return new PaymentDto.Response(r.getReceiptNo(), r.getConsignment().getCfrNo(), r.getAmount(), r.getMode(),
                r.getChequeOrTxnRef(), r.getChequeOrTxnRef(), r.getBranchCode(), r.getGeneratedByAgentId(),
                Boolean.TRUE.equals(r.getVerified()), r.getReceivedAt());
    }

    public static ComplianceDto.KycResponse toResponse(KycDocument d) {
        return new ComplianceDto.KycResponse(d.getType(), MaskingUtil.mask(d.getDocumentNumber()), d.getPhotoCount(), d.isVerified());
    }

    public static ComplianceDto.VehicleChecklistResponse toResponse(VehicleChecklist v) {
        return new ComplianceDto.VehicleChecklistResponse(v.getSurveyItem().getId(), v.getSurveyItem().getDescription(),
                v.getSurveyItem().getCategory(), v.isRcBook(), v.isValidInsurance(), v.isPucCertificate(),
                v.isRoadTaxReceipt(), v.getFuelTankEmpty(), v.getFuelLiters());
    }

    public static ComplianceDto.MasterArticleResponse toResponse(MasterArticleDeclaration d) {
        return new ComplianceDto.MasterArticleResponse(d.getSignedBy(), d.getSignedAt(),
                d.getLines().stream().map(l -> new ComplianceDto.ArticleLineDto(l.getSurveyItemId(), l.getDeclaredValue(),
                        l.getRiskCoverageAvailed())).toList());
    }

    public static ArticleLine toDomain(ComplianceDto.ArticleLineDto l) {
        return new ArticleLine(l.surveyItemId(), l.declaredValue(), l.riskCoverageAvailed());
    }

    public static TransitDto.LogResponse toResponse(TransitLog t) {
        return new TransitDto.LogResponse(t.getLocation(), t.getRemarks(), t.getStatusAtLog(), t.getLoggedAt());
    }

    public static TransitDto.ClaimResponse toResponse(DamageClaim c) {
        return new TransitDto.ClaimResponse(c.getId(), c.getSurveyItem().getId(), c.getDamageType(), c.getClaimedAmount(),
                c.getApprovedAmount(), c.getStatus(), c.getLodgedAt(), c.getBaselineEvidenceCount(), c.getDeliveryEvidenceCount());
    }

    public static GcnDto.Response toResponse(GoodsConsignmentNote g) {
        Map<PackingMaterial, Integer> returnable = new LinkedHashMap<>();
        g.getPackingMaterialsUsed().forEach((k, v) -> { if (k.isReturnable()) returnable.put(k, v); });
        return new GcnDto.Response(g.getConsignment().getCfrNo(), g.getGcnSerialNo(), g.getGcnDate(), g.getIbaCode(),
                g.getCarriageActRegNo(), g.getGstin(), g.getSacCode(), g.getCfrReferenceNo(), g.getLiabilitySection(),
                g.getDeclaredGoodsValue(), g.getDeliveryMode(), g.getUnloadingBy(), g.getPaymentBillingType(),
                g.getTotalPackages(), g.getActualWeightKg(), g.getChargedWeightKg(), g.getLengthFt(), g.getBreadthFt(),
                g.getHeightFt(), new LinkedHashMap<>(g.getPackingMaterialsUsed()), returnable, g.getTruckVehicleNo(),
                g.getCubeNo(), g.getSealNo(), g.getDispatchedAt(), g.getDigitalGcNo());
    }

    public static MediaDto.Response toResponse(InspectionMedia m) {
        return new MediaDto.Response(m.getId(), m.getStage(), m.getSurveyItem() == null ? null : m.getSurveyItem().getId(),
                m.getSurveyItem() == null ? null : m.getSurveyItem().getDescription(), m.getFileReference(),
                m.getCapturedAt(), m.getRemarks());
    }

    public static TrackingDto.EventResponse toResponse(TrackingEvent e) {
        return new TrackingDto.EventResponse(e.getId(), e.getDigitalGcNo(), e.getMilestone(), e.getEventCode(),
                e.getEventDate(), e.getEventTimestamp(), e.getHubCity(), e.getEventDescription(), e.getTruckVehicleNo(),
                e.getRecordedByAgentId());
    }

    /** Phone numbers are always masked; no caller is authenticated as hub staff. */
    public static CoordinatorDto.Response toResponse(CoordinatorContact c) {
        return new CoordinatorDto.Response(c.getRole(), c.getName(), c.getBranchCode(), MaskingUtil.mask(c.getPhoneNumber()),
                c.getAssignedAt());
    }

    public static PodDto.Response toResponse(DeliveryPodRecord p) {
        return new PodDto.Response(p.getConsignment().getCfrNo(), p.isOriginalRcVerified(), p.isOriginalInsuranceVerified(),
                p.isOriginalPucVerified(), p.isConsigneeIdVerified(), p.getConsigneeIdMaskedRef(),
                p.isPaymentReceiptsVerified(), p.isUnpackedAndInspectedBeforeSignOff(), p.getPodSignedBy(),
                p.getPodSignedAt(), p.getDeliveryRemarks(), p.isDamageNotedOnPod());
    }
}
