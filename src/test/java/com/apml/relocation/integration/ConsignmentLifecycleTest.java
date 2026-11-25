package com.apml.relocation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.enums.RestrictedItem;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.UnloadingBy;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.exception.ClaimRejectedException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.InsufficientPaymentException;
import com.apml.relocation.exception.InvalidStateTransitionException;
import com.apml.relocation.service.ClaimService;
import com.apml.relocation.service.ComplianceService;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.EnquiryService;
import com.apml.relocation.service.DeliveryPodService;
import com.apml.relocation.service.GcnService;
import com.apml.relocation.service.PaymentService;
import com.apml.relocation.support.TestData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fixture: vehicle (INR 50,000 declared) + carton (INR 5,000). Freight 10,000 + FOV 900 (750 + 150), GST exempted
 * => total payable INR 10,900.00; 10% advance = INR 1,090.00.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ConsignmentLifecycleTest {

    @Autowired EnquiryService enquiries;
    @Autowired ConsignmentService consignments;
    @Autowired PaymentService payments;
    @Autowired ComplianceService compliance;
    @Autowired ClaimService claims;
    @Autowired GcnService gcns;
    @Autowired Clock clock;
    @Autowired DeliveryPodService pods;

    // ------------------------------------------------------------------ helpers

    private Consignment quote(ItemCategory vehicle, int packingInDays) {
        LocalDate today = LocalDate.now(clock);
        List<EnquiryDto.ItemRequest> items = new ArrayList<>();
        items.add(new EnquiryDto.ItemRequest(vehicle, "Test Vehicle", 1, new BigDecimal("30.00"), true,
                new BigDecimal("50000"), new BigDecimal("50000")));
        items.add(new EnquiryDto.ItemRequest(ItemCategory.CARTON_BOX, "Test Carton", 1, new BigDecimal("4.00"), false,
                new BigDecimal("5000"), new BigDecimal("5000")));
        Enquiry e = enquiries.create(new EnquiryDto.CreateRequest(null, "Test Customer", "9876543210", "t@example.com",
                null, true, "Kolkata", "Hyderabad", "Pickup address", "Delivery address", MovementType.HOUSEHOLD_TPT, 1500, items,
                null, null, null, null));
        enquiries.approve(e.getEnquiryNo());
        Consignment c = consignments.create(new ConsignmentDto.CreateRequest(e.getEnquiryNo(), null,
                today.plusDays(packingInDays), today.plusDays(packingInDays + 1), new BigDecimal("10000.00"), false));
        return consignments.get(c.getCfrNo());
    }

    private SurveyItem item(Consignment c, boolean vehicle) {
        return c.getEnquiry().getItems().stream().filter(i -> i.getCategory().isVehicle() == vehicle).findFirst().orElseThrow();
    }

    private void pay(Consignment c, String amount) {
        payments.record(c.getCfrNo(), null, new BigDecimal(amount), PaymentMode.UPI, "TEST");
    }

    private void submitMasterArticle(Consignment c) {
        List<ArticleLine> lines = c.getEnquiry().getItems().stream()
                .map(i -> new ArticleLine(i.getId(), i.getDeclaredValue(), i.getDeclaredValue())).toList();
        compliance.submitMasterArticle(c.getCfrNo(), "Test Customer", Set.of(), lines);
    }

    private void completeCompliance(Consignment c) {
        String cfr = c.getCfrNo();
        boolean car = item(c, true).getCategory() == ItemCategory.FOUR_WHEELER;
        compliance.submitKyc(cfr, KycType.AADHAAR, "1234 5678 9012", null);
        compliance.submitKyc(cfr, KycType.PAN, "ABCDE1234F", null);
        compliance.submitKyc(cfr, KycType.PASSPORT_PHOTOS, null, car ? 4 : 2);
        compliance.updateVehicleChecklist(cfr, item(c, true).getId(), true, true, true, true,
                car ? null : Boolean.TRUE, car ? new BigDecimal("10.0") : null);
        submitMasterArticle(c);
    }

    // ------------------------------------------------------------------ pricing

    @Test
    void quotationTotalsAreComputedFromSurveyItems() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        assertThat(c.getFovChargeAmount()).isEqualByComparingTo("900.00");
        assertThat(c.getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(c.getTotalPayableAmount()).isEqualByComparingTo("10900.00");
        assertThat(c.getEnquiry().getTotalCft()).isEqualByComparingTo("34.00");
        assertThat(compliance.listVehicleChecklists(c.getCfrNo())).hasSize(1); // auto-triggered for vehicles
    }

    // ------------------------------------------------------------------ 10% booking rule

    @Test
    void bookingNeedsAtLeastTenPercentAdvance() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        pay(c, "1089.99");
        assertThrows(InsufficientPaymentException.class, () -> consignments.confirmBooking(c.getCfrNo()));
        assertThat(consignments.get(c.getCfrNo()).getStatus()).isEqualTo(ConsignmentStatus.QUOTED);

        pay(c, "0.01"); // exactly 1,090.00
        assertThat(consignments.confirmBooking(c.getCfrNo()).getStatus()).isEqualTo(ConsignmentStatus.BOOKING_CONFIRMED);
    }

    // ------------------------------------------------------------------ 100% pre-loading gate

    @Test
    void readyForLoadingRequiresFullPayment_thenDispatchAndDeliver() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        String cfr = c.getCfrNo();
        pay(c, "1090.00");
        consignments.confirmBooking(cfr);
        completeCompliance(c);
        consignments.verifyDocuments(cfr);

        pay(c, "4000.00");
        InsufficientPaymentException ex =
                assertThrows(InsufficientPaymentException.class, () -> consignments.markReadyForLoading(cfr));
        assertThat(ex.getExtensions().get("shortfall")).isEqualTo(new BigDecimal("5810.00"));

        pay(c, "5810.00");
        assertThat(consignments.markReadyForLoading(cfr).getStatus()).isEqualTo(ConsignmentStatus.READY_FOR_LOADING);
        gcns.issue(cfr, new GcnDto.IssueRequest(null, null, null, null, DeliveryMode.DOOR_DELIVERY, UnloadingBy.BY_CARRIER,
                null, null, null, null, null, null, null, null, null, null, null, null));
        assertThat(consignments.dispatch(cfr, new GcnDto.DispatchRequest("WB03D1234", null, "SEAL-001")).getStatus())
                .isEqualTo(ConsignmentStatus.IN_TRANSIT);
        pods.record(cfr, TestData.completePod());
        assertThat(consignments.deliver(cfr).getDeliveredAt()).isNotNull();
    }

    @Test
    void statusCannotSkipSteps() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        assertThrows(InvalidStateTransitionException.class, () -> consignments.markReadyForLoading(c.getCfrNo()));
    }

    // ------------------------------------------------------------------ vehicle documents & fuel

    @Test
    void missingDocumentsBlockVerification() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        pay(c, "1090.00");
        consignments.confirmBooking(c.getCfrNo());
        ComplianceViolationException ex =
                assertThrows(ComplianceViolationException.class, () -> consignments.verifyDocuments(c.getCfrNo()));
        assertThat(ex.getViolations()).anyMatch(v -> v.contains("RC_BOOK missing"))
                .anyMatch(v -> v.contains("AADHAAR")).anyMatch(v -> v.contains("Master Article List"));
    }

    @Test
    void twoWheelerFuelTankMustBeEmpty() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        Long id = item(c, true).getId();
        assertThrows(ComplianceViolationException.class,
                () -> compliance.updateVehicleChecklist(c.getCfrNo(), id, true, true, true, true, Boolean.FALSE, null));
        assertThrows(ComplianceViolationException.class,
                () -> compliance.updateVehicleChecklist(c.getCfrNo(), id, true, true, true, true, null, null));
        assertThat(compliance.updateVehicleChecklist(c.getCfrNo(), id, true, true, true, true, Boolean.TRUE, null)
                .getFuelTankEmpty()).isTrue();
    }

    @Test
    void fourWheelerFuelMustNotExceed24Litres() {
        Consignment c = quote(ItemCategory.FOUR_WHEELER, 10);
        Long id = item(c, true).getId();
        assertThrows(ComplianceViolationException.class,
                () -> compliance.updateVehicleChecklist(c.getCfrNo(), id, true, true, true, true, null, new BigDecimal("24.1")));
        assertThat(compliance.updateVehicleChecklist(c.getCfrNo(), id, true, true, true, true, null, new BigDecimal("24.0"))
                .getFuelLiters()).isEqualByComparingTo("24.0");
    }

    @Test
    void fourWheelerNeedsFourPassportPhotos() {
        Consignment c = quote(ItemCategory.FOUR_WHEELER, 10);
        assertThrows(ComplianceViolationException.class,
                () -> compliance.submitKyc(c.getCfrNo(), KycType.PASSPORT_PHOTOS, null, 2));
        assertThat(compliance.submitKyc(c.getCfrNo(), KycType.PASSPORT_PHOTOS, null, 4).isVerified()).isTrue();
    }

    // ------------------------------------------------------------------ master article list

    @Test
    void restrictedItemsRejectMasterArticleList() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        assertThrows(ComplianceViolationException.class, () -> compliance.submitMasterArticle(
                c.getCfrNo(), "Test Customer", Set.of(RestrictedItem.JEWELRY), List.of()));
    }

    @Test
    void masterArticleListMustBeSubmittedAtLeast24HoursBeforePacking() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 1); // packing = tomorrow 00:00 => < 24h away
        assertThrows(ComplianceViolationException.class, () -> submitMasterArticle(c));
    }

    @Test
    void itemsOmittedFromMasterArticleListDefaultToZeroDeclaredValue() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        SurveyItem vehicle = item(c, true);
        SurveyItem carton = item(c, false);
        var decl = compliance.submitMasterArticle(c.getCfrNo(), "Test Customer", Set.of(),
                List.of(new ArticleLine(vehicle.getId(), new BigDecimal("50000"), new BigDecimal("50000"))));
        assertThat(decl.coverageFor(vehicle.getId())).isEqualByComparingTo("50000");
        assertThat(decl.coverageFor(carton.getId())).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    void cancelFourOrMoreDaysBeforePacking_fullRefund() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        pay(c, "2000.00");
        Consignment cancelled = consignments.cancel(c.getCfrNo(), "Plans changed");
        assertThat(cancelled.getStatus()).isEqualTo(ConsignmentStatus.CANCELLED);
        assertThat(cancelled.getCancellationCharge()).isEqualByComparingTo("0.00");
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void cancelWithinThreeDays_tenPercentOfTotalDeducted() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 2);
        pay(c, "2000.00");
        Consignment cancelled = consignments.cancel(c.getCfrNo(), "Late change");
        assertThat(cancelled.getCancellationCharge()).isEqualByComparingTo("1090.00");
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("910.00");
    }

    // ------------------------------------------------------------------ claims end to end (real clock, just delivered)

    @Test
    void freshlyDeliveredShipment_claimsFollowCoverageAndDamageRules() {
        Consignment c = quote(ItemCategory.TWO_WHEELER, 10);
        String cfr = c.getCfrNo();
        pay(c, "1090.00");
        consignments.confirmBooking(cfr);
        completeCompliance(c);
        consignments.verifyDocuments(cfr);
        pay(c, "9810.00");
        consignments.markReadyForLoading(cfr);
        gcns.issue(cfr, new GcnDto.IssueRequest(null, null, null, null, DeliveryMode.DOOR_DELIVERY, UnloadingBy.BY_CARRIER,
                null, null, null, null, null, null, null, null, null, null, null, null));
        consignments.dispatch(cfr, new GcnDto.DispatchRequest("WB03D1234", null, "SEAL-002"));
        pods.record(cfr, TestData.completePod());
        consignments.deliver(cfr);

        Long bike = item(c, true).getId();
        Long carton = item(c, false).getId();
        assertThat(claims.lodge(cfr, bike, DamageType.EXTERIOR, "Scratched tank", new BigDecimal("2500")).getApprovedAmount())
                .isEqualByComparingTo("2500");
        assertThrows(ClaimRejectedException.class,
                () -> claims.lodge(cfr, bike, DamageType.INTERNAL_MECHANICAL, "Engine fault", new BigDecimal("2500")));
        assertThat(claims.lodge(cfr, carton, DamageType.OTHER, "Crushed", new BigDecimal("9000")).getApprovedAmount())
                .isEqualByComparingTo("5000"); // capped at declared cover
    }
}
