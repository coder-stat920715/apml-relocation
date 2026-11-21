package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.MoneyReceipt;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.InsufficientPaymentException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.repository.MoneyReceiptRepository;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final BigDecimal ADVANCE_PERCENT = BigDecimal.TEN;

    private final MoneyReceiptRepository receipts;
    private final ConsignmentRepository consignments;
    private final GoodsConsignmentNoteRepository gcns;
    private final ReferenceNumberGenerator numbers;
    private final Clock clock;

    /** Backwards-compatible overload (no branch / agent / verification metadata). */
    @Transactional
    public MoneyReceipt record(String cfrNo, String receiptNo, BigDecimal amount, PaymentMode mode, String chequeOrTxnRef) {
        return record(cfrNo, receiptNo, amount, mode, chequeOrTxnRef, null, null, null);
    }

    /** Records one instalment; a consignment may carry any number of receipts until the balance reaches zero. */
    @Transactional
    public MoneyReceipt record(String cfrNo, String receiptNo, BigDecimal amount, PaymentMode mode, String chequeOrTxnRef,
                               String branchCode, String generatedByAgentId, Boolean verified) {
        Consignment c = load(cfrNo);
        if (c.getStatus() == ConsignmentStatus.CANCELLED || c.getStatus() == ConsignmentStatus.CLOSED) {
            throw new BusinessRuleException("Payments cannot be recorded against a " + c.getStatus() + " consignment");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Payment amount must be positive");
        }
        BigDecimal balance = balanceDue(c);
        if (amount.compareTo(balance) > 0) {
            throw new BusinessRuleException("Payment of " + amount + " exceeds outstanding balance of " + balance);
        }
        String no = receiptNo;
        if (no == null || no.isBlank()) {
            do { no = numbers.receiptNo(); } while (receipts.existsByReceiptNo(no));
        } else if (receipts.existsByReceiptNo(no)) {
            throw new DuplicateResourceException("Receipt already exists: " + no);
        }
        MoneyReceipt r = new MoneyReceipt();
        r.setReceiptNo(no);
        r.setConsignment(c);
        r.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        r.setMode(mode);
        r.setChequeOrTxnRef(chequeOrTxnRef);
        r.setBranchCode(branchCode);
        r.setGeneratedByAgentId(generatedByAgentId);
        r.setVerified(verified == null ? Boolean.TRUE : verified);
        r.setReceivedAt(LocalDateTime.now(clock));
        r = receipts.save(r);
        log.info("Receipt {} of INR {} ({}) recorded for {}", no, r.getAmount(), mode, cfrNo);
        syncBillingType(c);
        return r;
    }

    /** Marks a previously unverified receipt (e.g. a cheque awaiting clearance) as verified. */
    @Transactional
    public MoneyReceipt verify(String cfrNo, String receiptNo) {
        Consignment c = load(cfrNo);
        MoneyReceipt r = receipts.findByReceiptNo(receiptNo)
                .filter(x -> x.getConsignment().getId().equals(c.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Money receipt", receiptNo));
        r.setVerified(Boolean.TRUE);
        r = receipts.save(r);
        syncBillingType(c);
        return r;
    }

    @Transactional(readOnly = true)
    public List<MoneyReceipt> list(String cfrNo) {
        return receipts.findByConsignmentOrderByReceivedAtAscIdAsc(load(cfrNo));
    }

    /** Sum of VERIFIED receipts only. */
    @Transactional(readOnly = true)
    public BigDecimal totalPaid(Consignment c) {
        return receipts.findByConsignmentOrderByReceivedAtAscIdAsc(c).stream()
                .filter(r -> Boolean.TRUE.equals(r.getVerified()))
                .map(MoneyReceipt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceDue(Consignment c) {
        return c.getTotalPayableAmount().subtract(totalPaid(c));
    }

    public BigDecimal requiredAdvance(Consignment c) {
        return c.getTotalPayableAmount().multiply(ADVANCE_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Rule 1: at least 10% of total payable before BOOKING_CONFIRMED. */
    @Transactional(readOnly = true)
    public void assertAdvancePaid(Consignment c) {
        BigDecimal required = requiredAdvance(c);
        BigDecimal paid = totalPaid(c);
        if (paid.compareTo(required) < 0) {
            throw new InsufficientPaymentException(
                    "Minimum 10% advance of INR " + required + " is required to confirm booking; received INR " + paid,
                    required, paid);
        }
    }

    /** Rule 2: 100% of total payable before READY_FOR_LOADING / IN_TRANSIT. */
    @Transactional(readOnly = true)
    public void assertFullyPaid(Consignment c) {
        BigDecimal paid = totalPaid(c);
        if (paid.compareTo(c.getTotalPayableAmount()) < 0) {
            throw new InsufficientPaymentException(
                    "Full payment of INR " + c.getTotalPayableAmount() + " is required before loading; outstanding balance is INR "
                            + c.getTotalPayableAmount().subtract(paid),
                    c.getTotalPayableAmount(), paid);
        }
    }

    /** Delivery gate: nothing outstanding and at least one verified receipt on file. */
    @Transactional(readOnly = true)
    public void assertPaymentProof(Consignment c) {
        assertFullyPaid(c);
        boolean anyVerified = receipts.findByConsignmentOrderByReceivedAtAscIdAsc(c).stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getVerified()));
        if (!anyVerified) {
            throw new BusinessRuleException("Delivery requires at least one verified money receipt as payment proof");
        }
    }

    /** Once 100% is settled the Goods Consignment Note (if issued) is switched to billing type PAID. */
    @Transactional
    public void syncBillingType(Consignment c) {
        if (balanceDue(c).signum() > 0) return;
        gcns.findByConsignment(c).ifPresent(g -> {
            if (g.getPaymentBillingType() != PaymentBillingType.PAID) {
                g.setPaymentBillingType(PaymentBillingType.PAID);
                gcns.save(g);
            }
        });
    }

    /** Rule 3: cancellation deduction / refund preview. */
    @Transactional(readOnly = true)
    public CancellationPolicy.Quote cancellationQuote(Consignment c) {
        return CancellationPolicy.quote(c.getPackingDate(), LocalDate.now(clock), c.getTotalPayableAmount(), totalPaid(c));
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
