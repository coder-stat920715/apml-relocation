package com.apml.relocation.support;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.SurveyItem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Builders for in-memory entities used by the Mockito based unit tests. */
public final class TestData {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 12, 0);
    public static final String CFR = "CFRKOL260000001";
    public static final String ENQUIRY_NO = "EQKOL260000001";

    private TestData() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    }

    public static SurveyItem item(long id, ItemCategory category, String declared, String cover) {
        SurveyItem item = new SurveyItem();
        item.setId(id);
        item.setCategory(category);
        item.setDescription("Item " + id);
        item.setQuantity(1);
        item.setVolumeCft(new BigDecimal("10.00"));
        item.setDeclaredValue(new BigDecimal(declared));
        item.setRiskCoverageAvailed(new BigDecimal(cover));
        return item;
    }

    public static Enquiry enquiry(SurveyItem... items) {
        Enquiry e = new Enquiry();
        e.setId(1L);
        e.setEnquiryNo(ENQUIRY_NO);
        e.setCustomerName("Test Customer");
        e.setMobile("9876543210");
        e.setGstExempted(true);
        e.setOriginCity("Kolkata");
        e.setDestinationCity("Hyderabad");
        e.setMovementType(MovementType.HOUSEHOLD_TPT);
        e.setDistanceKm(1500);
        e.setStatus(EnquiryStatus.APPROVED);
        e.setSubmittedOn(NOW.toLocalDate());
        for (SurveyItem item : items) {
            e.addItem(item);
        }
        return e;
    }

    /** Total payable INR 7,000 (freight 6,775 + FOV 225), packing in 10 days. */
    public static Consignment consignment(ConsignmentStatus status, Enquiry enquiry) {
        Consignment c = new Consignment();
        c.setId(1L);
        c.setCfrNo(CFR);
        c.setEnquiry(enquiry);
        c.setStatus(status);
        c.setPackingDate(NOW.toLocalDate().plusDays(10));
        c.setLoadingDate(NOW.toLocalDate().plusDays(11));
        c.setGrossFreightAmount(new BigDecimal("6775.00"));
        c.setFovChargeAmount(new BigDecimal("225.00"));
        c.setGstAmount(BigDecimal.ZERO);
        c.setTotalPayableAmount(new BigDecimal("7000.00"));
        return c;
    }

    public static GoodsConsignmentNote gcn(Consignment consignment) {
        GoodsConsignmentNote g = new GoodsConsignmentNote();
        g.setId(1L);
        g.setConsignment(consignment);
        g.setGcnSerialNo(1354001L);
        g.setGcnDate(NOW);
        g.setCfrReferenceNo(consignment.getCfrNo());
        return g;
    }
}
