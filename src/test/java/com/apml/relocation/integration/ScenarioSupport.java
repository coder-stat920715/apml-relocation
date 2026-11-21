package com.apml.relocation.integration;

import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.enums.UnloadingBy;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.service.ComplianceService;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.EnquiryService;
import com.apml.relocation.service.GcnService;
import com.apml.relocation.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Test-only helper that builds consignments in a given state (total payable INR 7,000, GST exempt, FOV INR 225). */
@Component
public class ScenarioSupport {

    @Autowired EnquiryService enquiries;
    @Autowired ConsignmentService consignments;
    @Autowired PaymentService payments;
    @Autowired ComplianceService compliance;
    @Autowired GcnService gcns;
    @Autowired Clock clock;

    /** A freshly quoted consignment (status QUOTED). */
    public Consignment quoted() {
        LocalDate today = LocalDate.now(clock);
        var e = enquiries.create(new EnquiryDto.CreateRequest(null, "Scenario Customer", "9876543210", "s@example.com", null, true,
                "Kolkata", "Hyderabad", "Pickup", "Delivery", MovementType.HOUSEHOLD_TPT, 1500,
                List.of(new EnquiryDto.ItemRequest(ItemCategory.TWO_WHEELER, "Scenario Bike", 1, new BigDecimal("37.05"), true,
                                new BigDecimal("5000"), new BigDecimal("5000")),
                        new EnquiryDto.ItemRequest(ItemCategory.CARTON_BOX, "Scenario Carton", 1, new BigDecimal("3.92"), false,
                                new BigDecimal("5000"), new BigDecimal("5000"))),
                null, null, null, null));
        enquiries.approve(e.getEnquiryNo());
        Consignment c = consignments.create(new ConsignmentDto.CreateRequest(e.getEnquiryNo(), null,
                today.plusDays(10), today.plusDays(11), new BigDecimal("6775.00"), false));
        return consignments.get(c.getCfrNo());
    }

    /** READY_FOR_LOADING with two receipts (700 + 6,300) and a GCN that still lacks truck and seal details. */
    public String readyWithGcn() {
        Consignment c = quoted();
        String cfr = c.getCfrNo();
        payments.record(cfr, null, new BigDecimal("700.00"), PaymentMode.UPI, "APMEB-1", "KOL", "APM11879", true);
        consignments.confirmBooking(cfr);
        compliance.submitKyc(cfr, KycType.AADHAAR, "123456789012", null);
        compliance.submitKyc(cfr, KycType.PAN, "ABCDE1234F", null);
        compliance.submitKyc(cfr, KycType.PASSPORT_PHOTOS, null, 2);
        var bike = c.getEnquiry().getItems().stream().filter(i -> i.getCategory().isVehicle()).findFirst().orElseThrow();
        compliance.updateVehicleChecklist(cfr, bike.getId(), true, true, true, true, Boolean.TRUE, null);
        compliance.submitMasterArticle(cfr, "Scenario Customer", Set.of(), c.getEnquiry().getItems().stream()
                .map(i -> new ArticleLine(i.getId(), i.getDeclaredValue(), i.getDeclaredValue())).toList());
        consignments.verifyDocuments(cfr);
        payments.record(cfr, null, new BigDecimal("6300.00"), PaymentMode.NEFT_RTGS, "APMEB-2", "KOL", "APM11879", true);
        consignments.markReadyForLoading(cfr);
        gcns.issue(cfr, new GcnDto.IssueRequest(null, null, null, null, DeliveryMode.DOOR_DELIVERY, UnloadingBy.BY_CARRIER, null,
                2, new BigDecimal("44.00"), new BigDecimal("100.00"), null, null, null, null, null, null, null, null));
        return cfr;
    }
}
