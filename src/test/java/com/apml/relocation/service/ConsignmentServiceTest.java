package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.InvalidStateTransitionException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.EnquiryRepository;
import com.apml.relocation.repository.TransitLogRepository;
import com.apml.relocation.support.TestData;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsignmentServiceTest {

    private static final String CFR = TestData.CFR;
    private static final LocalDate TODAY = TestData.NOW.toLocalDate();

    @Mock ConsignmentRepository consignments;
    @Mock EnquiryRepository enquiries;
    @Mock TransitLogRepository transitLogs;
    @Mock PaymentService payments;
    @Mock ComplianceService compliance;
    @Mock GcnService gcn;
    @Mock ReferenceNumberGenerator numbers;

    private ConsignmentService service;
    private Enquiry enquiry;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new ConsignmentService(consignments, enquiries, transitLogs, new PricingService(new BigDecimal("0.05")),
                payments, compliance, gcn, numbers, TestData.clock());
        enquiry = TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000"),
                TestData.item(2, ItemCategory.CARTON_BOX, "5000", "5000"));
        consignment = TestData.consignment(ConsignmentStatus.QUOTED, enquiry);
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(enquiry));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(consignments.save(any(Consignment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ConsignmentDto.CreateRequest create(String cfrNo, int packingInDays, int loadingInDays, boolean ownerRisk) {
        return new ConsignmentDto.CreateRequest(TestData.ENQUIRY_NO, cfrNo, TODAY.plusDays(packingInDays),
                TODAY.plusDays(loadingInDays), new BigDecimal("6775.00"), ownerRisk);
    }

    // ------------------------------------------------------------------ create

    @Test
    void createQuotesWithFovAndConvertsEnquiry() {
        Consignment c = service.create(create(CFR, 10, 11, false));
        assertThat(c.getCfrNo()).isEqualTo(CFR);
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.QUOTED);
        assertThat(c.getFovChargeAmount()).isEqualByComparingTo("225.00");
        assertThat(c.getGstAmount()).isEqualByComparingTo("0");
        assertThat(c.getTotalPayableAmount()).isEqualByComparingTo("7000.00");
        assertThat(c.isOwnerRisk()).isFalse();
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.CONVERTED);
        verify(compliance).initVehicleChecklists(c);
        verify(enquiries).save(enquiry);
    }

    @Test
    void createAtOwnersRiskChargesNoFovAndAppliesGstWhenNotExempt() {
        enquiry.setGstExempted(false);
        Consignment c = service.create(create(CFR, 10, 11, true));
        assertThat(c.getFovChargeAmount()).isEqualByComparingTo("0");
        assertThat(c.getGstAmount()).isEqualByComparingTo("338.75");
        assertThat(c.getTotalPayableAmount()).isEqualByComparingTo("7113.75");
    }

    @Test
    void createGeneratesCfrNumberRetryingOnCollision() {
        when(numbers.cfrNo("Kolkata")).thenReturn("CFRKOL260000001", "CFRKOL260000002");
        when(consignments.existsByCfrNo("CFRKOL260000001")).thenReturn(true);
        assertThat(service.create(create(null, 10, 11, false)).getCfrNo()).isEqualTo("CFRKOL260000002");
    }

    @Test
    void createTreatsBlankCfrAsMissing() {
        when(numbers.cfrNo("Kolkata")).thenReturn("CFRKOL260000007");
        assertThat(service.create(create("", 10, 11, false)).getCfrNo()).isEqualTo("CFRKOL260000007");
    }

    @Test
    void createRejectsDuplicateSuppliedCfr() {
        when(consignments.existsByCfrNo("CFRKOL262706738")).thenReturn(true);
        ConsignmentDto.CreateRequest request = create("CFRKOL262706738", 10, 11, false);
        assertThrows(DuplicateResourceException.class, () -> service.create(request));
    }

    @Test
    void createFailsForUnknownEnquiry() {
        ConsignmentDto.CreateRequest request = new ConsignmentDto.CreateRequest("NOPE", null, TODAY, TODAY, BigDecimal.TEN, false);
        assertThrows(ResourceNotFoundException.class, () -> service.create(request));
    }

    @Test
    void createRejectsConvertedAndUnapprovedEnquiries() {
        ConsignmentDto.CreateRequest request = create(CFR, 10, 11, false);
        enquiry.setStatus(EnquiryStatus.CONVERTED);
        assertThrows(BusinessRuleException.class, () -> service.create(request));
        enquiry.setStatus(EnquiryStatus.SUBMITTED);
        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void createRejectsExpiredQuotation() {
        enquiry.setSubmittedOn(TODAY.minusDays(16));
        ConsignmentDto.CreateRequest request = create(CFR, 10, 11, false);
        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void createRejectsInvalidDates() {
        ConsignmentDto.CreateRequest pastPacking = create(CFR, -1, 1, false);
        ConsignmentDto.CreateRequest loadingBeforePacking = create(CFR, 5, 4, false);
        ConsignmentDto.CreateRequest beyondThirtyDays = create(CFR, 10, 31, false);
        assertThrows(BusinessRuleException.class, () -> service.create(pastPacking));
        assertThrows(BusinessRuleException.class, () -> service.create(loadingBeforePacking));
        assertThrows(BusinessRuleException.class, () -> service.create(beyondThirtyDays));
    }

    @Test
    void createAcceptsLoadingOnTheThirtiethDay() {
        assertThat(service.create(create(CFR, 29, 30, false)).getLoadingDate()).isEqualTo(TODAY.plusDays(30));
    }

    // ------------------------------------------------------------------ lookups and simple transitions

    @Test
    void getFindsOrFails() {
        assertThat(service.get(CFR)).isSameAs(consignment);
        assertThrows(ResourceNotFoundException.class, () -> service.get("NOPE"));
    }

    @Test
    void confirmBookingChecksAdvanceThenMoves() {
        assertThat(service.confirmBooking(CFR).getStatus()).isEqualTo(ConsignmentStatus.BOOKING_CONFIRMED);
        verify(payments).assertAdvancePaid(consignment);
    }

    @Test
    void confirmBookingRejectsWrongState() {
        consignment.setStatus(ConsignmentStatus.DELIVERED);
        assertThrows(InvalidStateTransitionException.class, () -> service.confirmBooking(CFR));
        verify(payments, never()).assertAdvancePaid(any());
    }

    @Test
    void verifyDocumentsChecksComplianceThenMoves() {
        consignment.setStatus(ConsignmentStatus.BOOKING_CONFIRMED);
        assertThat(service.verifyDocuments(CFR).getStatus()).isEqualTo(ConsignmentStatus.DOCUMENTS_VERIFIED);
        verify(compliance).assertCompliant(consignment);
    }

    @Test
    void verifyDocumentsRejectsWrongState() {
        assertThrows(InvalidStateTransitionException.class, () -> service.verifyDocuments(CFR));
    }

    @Test
    void markReadyChecksPaymentAndComplianceThenMoves() {
        consignment.setStatus(ConsignmentStatus.DOCUMENTS_VERIFIED);
        assertThat(service.markReadyForLoading(CFR).getStatus()).isEqualTo(ConsignmentStatus.READY_FOR_LOADING);
        verify(payments).assertFullyPaid(consignment);
        verify(compliance).assertCompliant(consignment);
    }

    @Test
    void markReadyRejectsWrongState() {
        assertThrows(InvalidStateTransitionException.class, () -> service.markReadyForLoading(CFR));
    }

    @Test
    void closeMovesDeliveredConsignment() {
        consignment.setStatus(ConsignmentStatus.DELIVERED);
        assertThat(service.close(CFR).getStatus()).isEqualTo(ConsignmentStatus.CLOSED);
    }

    @Test
    void closeRejectsWrongState() {
        assertThrows(InvalidStateTransitionException.class, () -> service.close(CFR));
    }

    // ------------------------------------------------------------------ dispatch

    private GoodsConsignmentNote readyToDispatch() {
        consignment.setStatus(ConsignmentStatus.READY_FOR_LOADING);
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTruckVehicleNo("WB03D1234");
        g.setSealNo("SEAL-1");
        when(gcn.applyDispatchDetails(any(Consignment.class), any())).thenReturn(g);
        return g;
    }

    @Test
    void dispatchEnforcesGatesMarksDispatchAndLogsDeparture() {
        GoodsConsignmentNote g = readyToDispatch();
        GcnDto.DispatchRequest details = new GcnDto.DispatchRequest("WB03D1234", null, "SEAL-1");
        assertThat(service.dispatch(CFR, details).getStatus()).isEqualTo(ConsignmentStatus.IN_TRANSIT);
        verify(payments).assertFullyPaid(consignment);
        verify(payments).syncBillingType(consignment);
        verify(gcn).assertDispatchReady(consignment, g);
        verify(gcn).markDispatched(g);
        verify(gcn).applyDispatchDetails(consignment, details);
        verify(transitLogs).save(any(TransitLog.class));
    }

    @Test
    void dispatchWithoutBodyDelegatesWithNullDetails() {
        readyToDispatch();
        assertThat(service.dispatch(CFR).getStatus()).isEqualTo(ConsignmentStatus.IN_TRANSIT);
        verify(gcn).applyDispatchDetails(consignment, null);
    }

    @Test
    void dispatchRejectsWrongState() {
        assertThrows(InvalidStateTransitionException.class, () -> service.dispatch(CFR, null));
        verify(payments, never()).assertFullyPaid(any());
    }

    // ------------------------------------------------------------------ hub arrival

    @Test
    void hubArrivalRequiresInTransit() {
        assertThrows(BusinessRuleException.class, () -> service.recordHubArrival(CFR, null));
    }

    @Test
    void hubArrivalDefaultsToNowAndIsLogged() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        Consignment c = service.recordHubArrival(CFR, null);
        assertThat(c.getArrivedAtDestinationHubAt()).isEqualTo(TestData.NOW);
        verify(transitLogs).save(any(TransitLog.class));
    }

    @Test
    void hubArrivalAcceptsBackDatedScan() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        LocalDateTime earlier = TestData.NOW.minusDays(2);
        assertThat(service.recordHubArrival(CFR, earlier).getArrivedAtDestinationHubAt()).isEqualTo(earlier);
    }

    @Test
    void hubArrivalCannotBeRecordedTwiceOrInTheFuture() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        LocalDateTime future = TestData.NOW.plusHours(1);
        assertThrows(BusinessRuleException.class, () -> service.recordHubArrival(CFR, future));
        consignment.setArrivedAtDestinationHubAt(TestData.NOW.minusDays(1));
        assertThrows(BusinessRuleException.class, () -> service.recordHubArrival(CFR, null));
    }

    // ------------------------------------------------------------------ deliver and demurrage

    private void inTransitArrivedDaysAgo(long days) {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        consignment.setArrivedAtDestinationHubAt(TestData.NOW.minusDays(days));
    }

    @Test
    void deliverRejectsWrongState() {
        assertThrows(InvalidStateTransitionException.class, () -> service.deliver(CFR));
    }

    @Test
    void deliverWithoutHubArrivalChargesNoDemurrage() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        Consignment c = service.deliver(CFR);
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.DELIVERED);
        assertThat(c.getDeliveredAt()).isEqualTo(TestData.NOW);
        assertThat(c.getDemurrageAmount()).isNull();
        verify(payments).assertPaymentProof(consignment);
    }

    @Test
    void deliverWithoutGcnFallsBackToZeroPackagesAndWeight() {
        inTransitArrivedDaysAgo(4);
        when(gcn.find(consignment)).thenReturn(null);
        Consignment c = service.deliver(CFR);
        assertThat(c.getDemurrageDays()).isEqualTo(3);
        assertThat(c.getDemurrageAmount()).isEqualByComparingTo("0");
    }

    @Test
    void deliverWithIncompleteGcnChargesNothing() {
        inTransitArrivedDaysAgo(4);
        when(gcn.find(consignment)).thenReturn(TestData.gcn(consignment));
        assertThat(service.deliver(CFR).getDemurrageAmount()).isEqualByComparingTo("0");
    }

    @Test
    void deliverChargesPerPackageWhenThatIsHigher() {
        inTransitArrivedDaysAgo(4);
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTotalPackages(2);
        g.setChargedWeightKg(new BigDecimal("100"));
        when(gcn.find(consignment)).thenReturn(g);
        Consignment c = service.deliver(CFR);
        assertThat(c.getDemurrageDays()).isEqualTo(3);
        assertThat(c.getDemurrageAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void deliverFallsBackToActualWeightWhenChargedWeightIsMissing() {
        inTransitArrivedDaysAgo(4);
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTotalPackages(2);
        g.setActualWeightKg(new BigDecimal("750"));
        when(gcn.find(consignment)).thenReturn(g);
        assertThat(service.deliver(CFR).getDemurrageAmount()).isEqualByComparingTo("112.50");
    }

    @Test
    void deliverWithinFreeDayChargesNothing() {
        inTransitArrivedDaysAgo(1);
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTotalPackages(2);
        when(gcn.find(consignment)).thenReturn(g);
        Consignment c = service.deliver(CFR);
        assertThat(c.getDemurrageDays()).isZero();
        assertThat(c.getDemurrageAmount()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    void previewCancellationDelegatesToPaymentPolicy() {
        CancellationPolicy.Quote quote = new CancellationPolicy.Quote(10, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700"));
        when(payments.cancellationQuote(consignment)).thenReturn(quote);
        assertThat(service.previewCancellation(CFR)).isSameAs(quote);
    }

    @Test
    void previewCancellationRejectsNonCancellableStatus() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        assertThrows(InvalidStateTransitionException.class, () -> service.previewCancellation(CFR));
    }

    @Test
    void cancelRecordsChargeRefundAndReason() {
        when(payments.cancellationQuote(consignment))
                .thenReturn(new CancellationPolicy.Quote(2, BigDecimal.TEN, new BigDecimal("700.00"), new BigDecimal("100.00")));
        Consignment c = service.cancel(CFR, "Plans changed");
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.CANCELLED);
        assertThat(c.getCancellationCharge()).isEqualByComparingTo("700.00");
        assertThat(c.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(c.getCancellationReason()).isEqualTo("Plans changed");
        assertThat(c.getCancelledAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void cancelRejectsNonCancellableStatus() {
        consignment.setStatus(ConsignmentStatus.DELIVERED);
        assertThrows(InvalidStateTransitionException.class, () -> service.cancel(CFR, "late"));
    }
}
