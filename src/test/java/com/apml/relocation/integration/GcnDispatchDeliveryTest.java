package com.apml.relocation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.LiabilitySection;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.enums.UnloadingBy;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.InsufficientPaymentException;
import com.apml.relocation.service.ComplianceService;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.EnquiryService;
import com.apml.relocation.service.GcnService;
import com.apml.relocation.service.MediaService;
import com.apml.relocation.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fixture: bike (declared/cover INR 5,000) + carton (INR 5,000); freight 6,775 + FOV 225 => total INR 7,000, GST exempt.
 */
@SpringBootTest
@ActiveProfiles("dev")
class GcnDispatchDeliveryTest {

    @Autowired EnquiryService enquiries;
    @Autowired ConsignmentService consignments;
    @Autowired PaymentService payments;
    @Autowired ComplianceService compliance;
    @Autowired GcnService gcns;
    @Autowired MediaService media;
    @Autowired Clock clock;

    // ------------------------------------------------------------------ helpers

    private Consignment quote(boolean ownerRisk) {
        LocalDate today = LocalDate.now(clock);
        var items = List.of(
                new EnquiryDto.ItemRequest(ItemCategory.TWO_WHEELER, "Test Bike", 1, new BigDecimal("37.05"), true,
                        new BigDecimal("5000"), new BigDecimal("5000")),
                new EnquiryDto.ItemRequest(ItemCategory.CARTON_BOX, "Test Carton", 1, new BigDecimal("3.92"), false,
                        new BigDecimal("5000"), new BigDecimal("5000")));
        var e = enquiries.create(new EnquiryDto.CreateRequest(null, "Gcn Customer", "9876543210", "g@example.com", null, true,
                "Kolkata", "Hyderabad", "Pickup", "Delivery", MovementType.HOUSEHOLD_TPT, 1500, items, null, null, null, null));
        enquiries.approve(e.getEnquiryNo());
        Consignment c = consignments.create(new ConsignmentDto.CreateRequest(e.getEnquiryNo(), null,
                today.plusDays(10), today.plusDays(11), new BigDecimal(ownerRisk ? "7000.00" : "6775.00"), ownerRisk));
        return consignments.get(c.getCfrNo());
    }

    private void pay(Consignment c, String amount, String ref) {
        payments.record(c.getCfrNo(), null, new BigDecimal(amount), PaymentMode.UPI, ref, "KOL", "APM11879", true);
    }

    /** Pays 10%, confirms, completes compliance, verifies documents; leaves status DOCUMENTS_VERIFIED. */
    private Consignment documentsVerified(Consignment c) {
        String cfr = c.getCfrNo();
        pay(c, "700.00", "APMEB-1");
        consignments.confirmBooking(cfr);
        compliance.submitKyc(cfr, KycType.AADHAAR, "123456789012", null);
        compliance.submitKyc(cfr, KycType.PAN, "ABCDE1234F", null);
        compliance.submitKyc(cfr, KycType.PASSPORT_PHOTOS, null, 2);
        var bike = c.getEnquiry().getItems().stream().filter(i -> i.getCategory().isVehicle()).findFirst().orElseThrow();
        compliance.updateVehicleChecklist(cfr, bike.getId(), true, true, true, true, Boolean.TRUE, null);
        compliance.submitMasterArticle(cfr, "Gcn Customer", Set.of(), c.getEnquiry().getItems().stream()
                .map(i -> new ArticleLine(i.getId(), i.getDeclaredValue(), i.getDeclaredValue())).toList());
        consignments.verifyDocuments(cfr);
        return consignments.get(cfr);
    }

    private Consignment readyForLoading(Consignment c) {
        documentsVerified(c);
        pay(c, "6300.00", "APMEB-2");
        return consignments.markReadyForLoading(c.getCfrNo());
    }

    private GcnDto.IssueRequest gcnRequest(LiabilitySection liability, DeliveryMode mode) {
        return new GcnDto.IssueRequest(null, null, liability, null, mode, UnloadingBy.BY_CARRIER, null, 2,
                new BigDecimal("44.00"), new BigDecimal("100.00"), new BigDecimal("6"), new BigDecimal("2"), new BigDecimal("3.5"),
                Map.of(PackingMaterial.CARTON_BOX, 1, PackingMaterial.HYGIENE_PAD, 1), null, null, null, null);
    }

    private static final GcnDto.DispatchRequest TRUCK = new GcnDto.DispatchRequest("WB03D1234", null, "SEAL-9001");

    // ------------------------------------------------------------------ GCN issuance rules

    @Test
    void insuredConsignment_autoEnforcesSection11_andPopulatesDeclaredValue() {
        Consignment c = quote(false);
        documentsVerified(c);
        GoodsConsignmentNote g = gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        assertThat(g.getLiabilitySection()).isEqualTo(LiabilitySection.SECTION_11_INSURED_RISK);
        assertThat(g.getDeclaredGoodsValue()).isEqualByComparingTo("10000");
        assertThat(g.getCfrReferenceNo()).isEqualTo(c.getCfrNo());
        assertThat(g.getGcnSerialNo()).isNotNull();
        assertThat(g.getIbaCode()).isEqualTo("MUA-1783");
        assertThat(g.getSacCode()).isEqualTo("996511");
    }

    @Test
    void insuredConsignment_rejectsSection10() {
        Consignment c = quote(false);
        documentsVerified(c);
        assertThrows(ComplianceViolationException.class,
                () -> gcns.issue(c.getCfrNo(), gcnRequest(LiabilitySection.SECTION_10_LIMITED_LIABILITY, DeliveryMode.DOOR_DELIVERY)));
    }

    @Test
    void ownerRiskConsignment_usesSection10_andRejectsSection11() {
        Consignment c = quote(true);
        documentsVerified(c);
        assertThrows(ComplianceViolationException.class,
                () -> gcns.issue(c.getCfrNo(), gcnRequest(LiabilitySection.SECTION_11_INSURED_RISK, DeliveryMode.DOOR_DELIVERY)));
        assertThat(gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY)).getLiabilitySection())
                .isEqualTo(LiabilitySection.SECTION_10_LIMITED_LIABILITY);
    }

    @Test
    void mismatchedCfrReference_isRejected() {
        Consignment c = quote(false);
        documentsVerified(c);
        var r = new GcnDto.IssueRequest(null, "CFRKOL260000000", null, null, DeliveryMode.DOOR_DELIVERY, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        assertThrows(BusinessRuleException.class, () -> gcns.issue(c.getCfrNo(), r));
    }

    @Test
    void gcnCannotBeIssuedBeforeBookingConfirmation() {
        Consignment c = quote(false);
        assertThrows(BusinessRuleException.class, () -> gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY)));
    }

    @Test
    void returnableMaterialIsTracked() {
        Consignment c = quote(false);
        documentsVerified(c);
        GoodsConsignmentNote g = gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        assertThat(g.getPackingMaterialsUsed()).containsEntry(PackingMaterial.HYGIENE_PAD, 1);
        assertThat(PackingMaterial.HYGIENE_PAD.isReturnable()).isTrue();
        assertThat(PackingMaterial.CARTON_BOX.isReturnable()).isFalse();
    }

    // ------------------------------------------------------------------ multi-receipt settlement

    @Test
    void twoReceiptsSettleBalance_andSwitchGcnToPaid() {
        Consignment c = quote(false);
        documentsVerified(c);
        GoodsConsignmentNote g = gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        assertThat(g.getPaymentBillingType()).isEqualTo(PaymentBillingType.TBB);
        assertThat(payments.balanceDue(c)).isEqualByComparingTo("6300.00");

        pay(c, "6300.00", "APMEB-2");
        assertThat(payments.balanceDue(c)).isEqualByComparingTo("0");
        assertThat(payments.list(c.getCfrNo())).hasSize(2)
                .extracting(r -> r.getChequeOrTxnRef()).containsExactly("APMEB-1", "APMEB-2");
        assertThat(gcns.get(c.getCfrNo()).getPaymentBillingType()).isEqualTo(PaymentBillingType.PAID);
    }

    @Test
    void unverifiedReceiptDoesNotCountUntilVerified() {
        Consignment c = quote(false);
        pay(c, "700.00", "APMEB-1");
        var r = payments.record(c.getCfrNo(), null, new BigDecimal("6300.00"), PaymentMode.CHEQUE, "CHQ-77", "KOL", "APM11879", false);
        assertThat(payments.balanceDue(c)).isEqualByComparingTo("6300.00");
        payments.verify(c.getCfrNo(), r.getReceiptNo());
        assertThat(payments.balanceDue(c)).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ dispatch gate

    @Test
    void dispatchWithoutGcn_isBlocked() {
        Consignment c = readyForLoading(quote(false));
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class,
                () -> consignments.dispatch(c.getCfrNo(), TRUCK));
        assertThat(ex.getViolations()).anyMatch(v -> v.contains("Goods Consignment Note"));
    }

    @Test
    void dispatchWithoutDoorOrGodownDeliveryMode_isBlocked() {
        Consignment c = readyForLoading(quote(false));
        gcns.issue(c.getCfrNo(), gcnRequest(null, null));
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class,
                () -> consignments.dispatch(c.getCfrNo(), TRUCK));
        assertThat(ex.getViolations()).anyMatch(v -> v.contains("deliveryMode"));
        assertThat(consignments.get(c.getCfrNo()).getStatus()).isEqualTo(ConsignmentStatus.READY_FOR_LOADING);
    }

    @Test
    void dispatchWithoutTruckAndSeal_isBlocked() {
        Consignment c = readyForLoading(quote(false));
        gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class,
                () -> consignments.dispatch(c.getCfrNo(), null));
        assertThat(ex.getViolations()).anyMatch(v -> v.contains("truckVehicleNo")).anyMatch(v -> v.contains("sealNo"));
    }

    @Test
    void invalidTruckNumber_isRejected() {
        Consignment c = readyForLoading(quote(false));
        gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        assertThrows(BusinessRuleException.class,
                () -> consignments.dispatch(c.getCfrNo(), new GcnDto.DispatchRequest("NOT A TRUCK!", null, "SEAL-1")));
    }

    @Test
    void dispatchRequiresFullPayment() {
        Consignment c = quote(false);
        documentsVerified(c);
        gcns.issue(c.getCfrNo(), gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        assertThrows(InsufficientPaymentException.class, () -> consignments.markReadyForLoading(c.getCfrNo()));
    }

    // ------------------------------------------------------------------ dispatch -> hub arrival -> delivery

    @Test
    void completeFlow_withDemurrageAfterLateCollection() {
        Consignment c = readyForLoading(quote(false));
        String cfr = c.getCfrNo();
        gcns.issue(cfr, gcnRequest(null, DeliveryMode.DOOR_DELIVERY));

        assertThat(consignments.dispatch(cfr, TRUCK).getStatus()).isEqualTo(ConsignmentStatus.IN_TRANSIT);
        GoodsConsignmentNote g = gcns.get(cfr);
        assertThat(g.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(g.getSealNo()).isEqualTo("SEAL-9001");
        assertThat(g.getDispatchedAt()).isNotNull();

        consignments.recordHubArrival(cfr, LocalDateTime.now(clock).minusDays(4));
        assertThrows(BusinessRuleException.class, () -> consignments.recordHubArrival(cfr, null));

        Consignment delivered = consignments.deliver(cfr);
        assertThat(delivered.getStatus()).isEqualTo(ConsignmentStatus.DELIVERED);
        assertThat(delivered.getDemurrageDays()).isEqualTo(3);
        // 2 packages * 5 * 3 days = 30 beats 1 quintal (100 kg charged) * 5 * 3 days = 15
        assertThat(delivered.getDemurrageAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void deliveryOnTimeHasNoDemurrage() {
        Consignment c = readyForLoading(quote(false));
        String cfr = c.getCfrNo();
        gcns.issue(cfr, gcnRequest(null, DeliveryMode.DOOR_DELIVERY));
        consignments.dispatch(cfr, TRUCK);
        consignments.recordHubArrival(cfr, null);
        assertThat(consignments.deliver(cfr).getDemurrageAmount()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ inspection media

    @Test
    void mediaStagesAreEnforcedAgainstConsignmentStatus() {
        Consignment c = quote(false);
        var bike = c.getEnquiry().getItems().stream().filter(i -> i.getCategory().isVehicle()).findFirst().orElseThrow();
        assertThat(media.add(c.getCfrNo(), new MediaDto.Request(MediaStage.PRE_PACKING, "file://a.jpg", bike.getId(), null, "ok")).getId())
                .isNotNull();
        assertThrows(BusinessRuleException.class,
                () -> media.add(c.getCfrNo(), new MediaDto.Request(MediaStage.UNPACKED_AT_DELIVERY, "file://b.jpg", null, null, null)));
        assertThat(media.list(c.getCfrNo())).hasSize(1);
    }
}
