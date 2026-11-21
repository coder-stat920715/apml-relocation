package com.apml.relocation.service;

import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.EnquiryRepository;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnquiryService {

    private final EnquiryRepository enquiries;
    private final ReferenceNumberGenerator numbers;
    private final Clock clock;

    @Transactional
    public Enquiry create(EnquiryDto.CreateRequest req) {
        String no = req.enquiryNo();
        if (no == null || no.isBlank()) {
            do { no = numbers.enquiryNo(req.originCity()); } while (enquiries.existsByEnquiryNo(no));
        } else if (enquiries.existsByEnquiryNo(no)) {
            throw new DuplicateResourceException("Enquiry already exists: " + no);
        }
        Enquiry e = new Enquiry();
        e.setEnquiryNo(no);
        e.setCustomerName(req.customerName());
        e.setMobile(req.mobile());
        e.setEmail(req.email());
        e.setGstNo(req.gstNo());
        e.setGstExempted(req.gstExempted());
        e.setOriginCity(req.originCity());
        e.setDestinationCity(req.destinationCity());
        e.setPickupAddress(req.pickupAddress());
        e.setDeliveryAddress(req.deliveryAddress());
        e.setMovementType(req.movementType());
        e.setDistanceKm(req.distanceKm());
        e.setSubmittedOn(LocalDate.now(clock));
        for (EnquiryDto.ItemRequest ir : req.items()) {
            SurveyItem i = new SurveyItem();
            i.setCategory(ir.category());
            i.setDescription(ir.description());
            i.setQuantity(ir.quantity());
            i.setVolumeCft(ir.volumeCft());
            i.setCrateRequired(ir.crateRequired());
            i.setDeclaredValue(ir.declaredValue());
            i.setRiskCoverageAvailed(ir.riskCoverageAvailed() == null ? BigDecimal.ZERO : ir.riskCoverageAvailed());
            e.addItem(i);
        }
        log.info("Enquiry {} created with {} item(s), {} CFT", no, e.getItems().size(), e.getTotalCft());
        return enquiries.save(e);
    }

    @Transactional(readOnly = true)
    public Enquiry get(String enquiryNo) {
        return enquiries.findByEnquiryNo(enquiryNo)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", enquiryNo));
    }

    @Transactional
    public Enquiry approve(String enquiryNo) {
        Enquiry e = get(enquiryNo);
        if (e.getStatus() != EnquiryStatus.SUBMITTED) {
            throw new BusinessRuleException("Enquiry is already " + e.getStatus());
        }
        if (LocalDate.now(clock).isAfter(e.getValidUntil())) {
            throw new BusinessRuleException("Enquiry validity of 15 days has expired on " + e.getValidUntil());
        }
        e.setStatus(EnquiryStatus.APPROVED);
        return enquiries.save(e);
    }
}
