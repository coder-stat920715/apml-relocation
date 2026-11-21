package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.MoneyReceipt;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.InsufficientPaymentException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
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
    private final ReferenceNumberGenerator numbers;
    private final Clock clock;

    @Transactional
    public MoneyReceipt record(String cfrNo, String receiptNo, BigDecimal amount, PaymentMode mode, String referenceNo) {
        Consignment c = consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
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
        r.setReferenceNo(referenceNo);
        r.setReceivedAt(LocalDateTime.now(clock));
        log.info("Receipt {} of INR {} ({}) recorded for {}", no, r.getAmount(), mode, cfrNo);
        return receipts.save(r);
    }

    @Transactional(readOnly = true)
    public List<MoneyReceipt> list(String cfrNo) {
        Consignment c = consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
        return receipts.findByConsignmentOrderByReceivedAtAsc(c);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPaid(Consignment c) {
        return receipts.findByConsignmentOrderByReceivedAtAsc(c).stream()
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

    /** Rule 3: cancellation deduction / refund preview. */
    @Transactional(readOnly = true)
    public CancellationPolicy.Quote cancellationQuote(Consignment c) {
        return CancellationPolicy.quote(c.getPackingDate(), LocalDate.now(clock), c.getTotalPayableAmount(), totalPaid(c));
    }
}
