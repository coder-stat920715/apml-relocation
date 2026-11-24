package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.MoneyReceipt;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.InsufficientPaymentException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.repository.MoneyReceiptRepository;
import com.apml.relocation.support.TestData;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
class PaymentServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock MoneyReceiptRepository receipts;
    @Mock ConsignmentRepository consignments;
    @Mock GoodsConsignmentNoteRepository gcns;
    @Mock ReferenceNumberGenerator numbers;

    private PaymentService service;
    private Consignment consignment;
    private final List<MoneyReceipt> existing = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PaymentService(receipts, consignments, gcns, numbers, TestData.clock());
        consignment = TestData.consignment(ConsignmentStatus.QUOTED,
                TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000")));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(receipts.findByConsignmentOrderByReceivedAtAscIdAsc(consignment)).thenReturn(existing);
        when(receipts.save(any(MoneyReceipt.class))).thenAnswer(i -> {
            MoneyReceipt saved = i.getArgument(0);
            if (!existing.contains(saved)) {
                existing.add(saved);
            }
            return saved;
        });
    }

    private MoneyReceipt receipt(String amount, Boolean verified) {
        MoneyReceipt r = new MoneyReceipt();
        r.setConsignment(consignment);
        r.setAmount(new BigDecimal(amount));
        r.setVerified(verified);
        return r;
    }

    // ------------------------------------------------------------------ record

    @Test
    void recordRejectsCancelledAndClosedConsignments() {
        consignment.setStatus(ConsignmentStatus.CANCELLED);
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, null, BigDecimal.TEN, PaymentMode.UPI, "r"));
        consignment.setStatus(ConsignmentStatus.CLOSED);
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, null, BigDecimal.TEN, PaymentMode.UPI, "r"));
    }

    @Test
    void recordRejectsMissingOrNonPositiveAmounts() {
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, null, null, PaymentMode.UPI, "r"));
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, null, BigDecimal.ZERO, PaymentMode.UPI, "r"));
    }

    @Test
    void recordRejectsAmountAboveOutstandingBalance() {
        existing.add(receipt("6000.00", true));
        assertThrows(BusinessRuleException.class,
                () -> service.record(CFR, null, new BigDecimal("1500.00"), PaymentMode.UPI, "r"));
    }

    @Test
    void recordFailsForUnknownConsignment() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.record("UNKNOWN", null, BigDecimal.TEN, PaymentMode.UPI, "r"));
    }

    @Test
    void recordGeneratesReceiptNumberAndRetriesOnCollision() {
        when(numbers.receiptNo()).thenReturn("ARVWB260000001", "ARVWB260000002");
        when(receipts.existsByReceiptNo("ARVWB260000001")).thenReturn(true);
        MoneyReceipt r = service.record(CFR, null, new BigDecimal("700"), PaymentMode.UPI, "APMEB-1");
        assertThat(r.getReceiptNo()).isEqualTo("ARVWB260000002");
        assertThat(r.getChequeOrTxnRef()).isEqualTo("APMEB-1");
        assertThat(r.getAmount()).isEqualByComparingTo("700.00");
        assertThat(r.getVerified()).isTrue();
        assertThat(r.getReceivedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void recordTreatsBlankReceiptNumberAsMissing() {
        when(numbers.receiptNo()).thenReturn("ARVWB260000009");
        MoneyReceipt r = service.record(CFR, "  ", new BigDecimal("700"), PaymentMode.UPI, null);
        assertThat(r.getReceiptNo()).isEqualTo("ARVWB260000009");
    }

    @Test
    void recordRejectsDuplicateSuppliedReceiptNumber() {
        when(receipts.existsByReceiptNo("ARVWB262707169")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> service.record(CFR, "ARVWB262707169", new BigDecimal("700"), PaymentMode.UPI, "r"));
    }

    @Test
    void recordStoresAllMetadataAndDefaultsVerifiedToTrue() {
        MoneyReceipt r = service.record(CFR, "ARVWB262707169", new BigDecimal("700"), PaymentMode.NEFT_RTGS,
                "APMEB-5093071311", "KOL", "APM11879", null);
        assertThat(r.getReceiptNo()).isEqualTo("ARVWB262707169");
        assertThat(r.getBranchCode()).isEqualTo("KOL");
        assertThat(r.getGeneratedByAgentId()).isEqualTo("APM11879");
        assertThat(r.getMode()).isEqualTo(PaymentMode.NEFT_RTGS);
        assertThat(r.getVerified()).isTrue();
    }

    @Test
    void recordHonoursExplicitUnverifiedFlag() {
        MoneyReceipt r = service.record(CFR, "R-1", new BigDecimal("700"), PaymentMode.CHEQUE, "CHQ-1", "KOL", "APM1", false);
        assertThat(r.getVerified()).isFalse();
    }

    @Test
    void recordSwitchesGcnToPaidOnceSettled() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setPaymentBillingType(PaymentBillingType.TBB);
        when(gcns.findByConsignment(consignment)).thenReturn(Optional.of(g));
        existing.add(receipt("6999.99", true));
        service.record(CFR, "R-2", new BigDecimal("0.01"), PaymentMode.UPI, "x");
        assertThat(g.getPaymentBillingType()).isEqualTo(PaymentBillingType.PAID);
    }

    // ------------------------------------------------------------------ verify / list

    @Test
    void verifyMarksReceiptVerified() {
        MoneyReceipt r = receipt("700", false);
        r.setReceiptNo("R-1");
        consignment.setId(5L);
        when(receipts.findByReceiptNo("R-1")).thenReturn(Optional.of(r));
        assertThat(service.verify(CFR, "R-1").getVerified()).isTrue();
    }

    @Test
    void verifyRejectsReceiptOfAnotherConsignmentOrUnknownReceipt() {
        Consignment other = TestData.consignment(ConsignmentStatus.QUOTED, consignment.getEnquiry());
        other.setId(99L);
        MoneyReceipt foreign = receipt("700", false);
        foreign.setConsignment(other);
        when(receipts.findByReceiptNo("R-FOREIGN")).thenReturn(Optional.of(foreign));
        when(receipts.findByReceiptNo("R-MISSING")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.verify(CFR, "R-FOREIGN"));
        assertThrows(ResourceNotFoundException.class, () -> service.verify(CFR, "R-MISSING"));
    }

    @Test
    void listReturnsReceiptsInOrder() {
        existing.add(receipt("1", true));
        assertThat(service.list(CFR)).isSameAs(existing);
    }

    // ------------------------------------------------------------------ amounts and gates

    @Test
    void totalPaidCountsVerifiedReceiptsOnly() {
        existing.add(receipt("700", true));
        existing.add(receipt("300", false));
        existing.add(receipt("200", null));
        assertThat(service.totalPaid(consignment)).isEqualByComparingTo("700.00");
        assertThat(service.balanceDue(consignment)).isEqualByComparingTo("6300.00");
    }

    @Test
    void requiredAdvanceIsTenPercent() {
        assertThat(service.requiredAdvance(consignment)).isEqualByComparingTo("700.00");
    }

    @Test
    void advanceGateNeedsTenPercent() {
        existing.add(receipt("699.99", true));
        InsufficientPaymentException ex =
                assertThrows(InsufficientPaymentException.class, () -> service.assertAdvancePaid(consignment));
        assertThat(ex.getExtensions()).containsKey("shortfall");
        existing.add(receipt("0.01", true));
        service.assertAdvancePaid(consignment);
        assertThat(service.totalPaid(consignment)).isEqualByComparingTo("700.00");
    }

    @Test
    void fullPaymentGateNeedsEverything() {
        existing.add(receipt("6999.99", true));
        assertThrows(InsufficientPaymentException.class, () -> service.assertFullyPaid(consignment));
        existing.add(receipt("0.01", true));
        service.assertFullyPaid(consignment);
        assertThat(service.balanceDue(consignment)).isEqualByComparingTo("0");
    }

    @Test
    void paymentProofNeedsFullPaymentAndAVerifiedReceipt() {
        assertThrows(InsufficientPaymentException.class, () -> service.assertPaymentProof(consignment));

        existing.add(receipt("7000.00", true));
        service.assertPaymentProof(consignment);
        assertThat(service.balanceDue(consignment)).isEqualByComparingTo("0");
    }

    @Test
    void paymentProofRejectsWhenNoVerifiedReceiptExists() {
        consignment.setTotalPayableAmount(BigDecimal.ZERO);
        assertThrows(BusinessRuleException.class, () -> service.assertPaymentProof(consignment));
    }

    // ------------------------------------------------------------------ billing type sync

    @Test
    void syncDoesNothingWhileBalanceIsOutstanding() {
        service.syncBillingType(consignment);
        verify(gcns, never()).findByConsignment(any());
    }

    @Test
    void syncIgnoresSettledConsignmentWithoutGcn() {
        existing.add(receipt("7000.00", true));
        when(gcns.findByConsignment(consignment)).thenReturn(Optional.empty());
        service.syncBillingType(consignment);
        verify(gcns, never()).save(any());
    }

    @Test
    void syncSavesOnlyWhenGcnIsNotAlreadyPaid() {
        existing.add(receipt("7000.00", true));
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setPaymentBillingType(PaymentBillingType.PAID);
        when(gcns.findByConsignment(consignment)).thenReturn(Optional.of(g));
        service.syncBillingType(consignment);
        verify(gcns, never()).save(any());

        g.setPaymentBillingType(PaymentBillingType.TO_PAY);
        service.syncBillingType(consignment);
        verify(gcns).save(g);
        assertThat(g.getPaymentBillingType()).isEqualTo(PaymentBillingType.PAID);
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    void cancellationQuoteUsesTheClockAndVerifiedPayments() {
        existing.add(receipt("700.00", true));
        CancellationPolicy.Quote quote = service.cancellationQuote(consignment);
        assertThat(quote.daysBeforePacking()).isEqualTo(10);
        assertThat(quote.deduction()).isEqualByComparingTo("0");
        assertThat(quote.refund()).isEqualByComparingTo("700.00");
    }
}
