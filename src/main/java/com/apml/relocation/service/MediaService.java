package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.InspectionMediaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<ConsignmentStatus> PACKING_STAGES = Set.of(ConsignmentStatus.QUOTED,
            ConsignmentStatus.BOOKING_CONFIRMED, ConsignmentStatus.DOCUMENTS_VERIFIED, ConsignmentStatus.READY_FOR_LOADING);
    private static final Set<ConsignmentStatus> DELIVERY_STAGES = Set.of(ConsignmentStatus.IN_TRANSIT,
            ConsignmentStatus.DELIVERED, ConsignmentStatus.CLOSED);

    private final ConsignmentRepository consignments;
    private final InspectionMediaRepository media;
    private final Clock clock;

    @Transactional
    public InspectionMedia add(String cfrNo, MediaDto.Request r) {
        Consignment c = load(cfrNo);
        boolean delivery = r.stage() == MediaStage.UNPACKED_AT_DELIVERY;
        if (delivery ? !DELIVERY_STAGES.contains(c.getStatus()) : !PACKING_STAGES.contains(c.getStatus())) {
            throw new BusinessRuleException(r.stage() + " evidence cannot be attached while the consignment is " + c.getStatus());
        }
        SurveyItem item = null;
        if (r.surveyItemId() != null) {
            item = c.getEnquiry().getItems().stream().filter(i -> i.getId().equals(r.surveyItemId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Survey item", String.valueOf(r.surveyItemId())));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (r.capturedAt() != null && r.capturedAt().isAfter(now)) {
            throw new BusinessRuleException("capturedAt cannot be in the future");
        }
        InspectionMedia m = new InspectionMedia();
        m.setConsignment(c);
        m.setSurveyItem(item);
        m.setStage(r.stage());
        m.setFileReference(r.fileReference());
        m.setCapturedAt(r.capturedAt() == null ? now : r.capturedAt());
        m.setRemarks(r.remarks());
        return media.save(m);
    }

    @Transactional(readOnly = true)
    public List<InspectionMedia> list(String cfrNo) {
        return media.findByConsignmentOrderByCapturedAtAsc(load(cfrNo));
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
