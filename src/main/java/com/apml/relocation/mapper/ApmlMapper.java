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
import com.apml.relocation.dto.ComplianceDto;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.PaymentDto;
import com.apml.relocation.dto.TransitDto;
import com.apml.relocation.util.MaskingUtil;
import java.math.BigDecimal;

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
                e.getValidUntil(), e.getMoveDeadline(), e.getItems().stream().map(ApmlMapper::toResponse).toList());
    }

    public static ConsignmentDto.Response toResponse(Consignment c, BigDecimal paid, BigDecimal requiredAdvance) {
        return new ConsignmentDto.Response(c.getCfrNo(), c.getEnquiry().getEnquiryNo(), c.getStatus(), c.getPackingDate(),
                c.getLoadingDate(), c.getEnquiry().getTotalCft(), c.getGrossFreightAmount(), c.getFovChargeAmount(),
                c.getGstAmount(), c.getTotalPayableAmount(), paid, c.getTotalPayableAmount().subtract(paid),
                requiredAdvance, c.isOwnerRisk(), c.getDeliveredAt(), c.getCancelledAt(), c.getCancellationCharge(),
                c.getRefundAmount());
    }

    public static PaymentDto.Response toResponse(MoneyReceipt r) {
        return new PaymentDto.Response(r.getReceiptNo(), r.getConsignment().getCfrNo(), r.getAmount(), r.getMode(),
                r.getReferenceNo(), r.getReceivedAt());
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
                c.getApprovedAmount(), c.getStatus(), c.getLodgedAt());
    }
}
