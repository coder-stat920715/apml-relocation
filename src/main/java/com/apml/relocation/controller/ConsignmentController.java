package com.apml.relocation.controller;

import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.PaymentDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consignments")
@RequiredArgsConstructor
@Tag(name = "Consignment (CFR) & Payments")
public class ConsignmentController {

    private final ConsignmentService service;
    private final PaymentService payments;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a CFR / quotation from an approved enquiry")
    public ConsignmentDto.Response create(@Valid @RequestBody ConsignmentDto.CreateRequest request) {
        return view(service.create(request));
    }

    @GetMapping("/{cfrNo}")
    @Operation(summary = "Fetch a consignment with payment position")
    public ConsignmentDto.Response get(@PathVariable String cfrNo) {
        return view(service.get(cfrNo));
    }

    // ------------------------------------------------------------ payments

    @PostMapping("/{cfrNo}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a money receipt (ONLINE, NEFT_RTGS, IMPS, UPI, CHEQUE, CARD)")
    public PaymentDto.Response pay(@PathVariable String cfrNo, @Valid @RequestBody PaymentDto.Request r) {
        return ApmlMapper.toResponse(payments.record(cfrNo, r.receiptNo(), r.amount(), r.mode(), r.referenceNo()));
    }

    @GetMapping("/{cfrNo}/payments")
    @Operation(summary = "List money receipts")
    public List<PaymentDto.Response> receipts(@PathVariable String cfrNo) {
        return payments.list(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }

    // ------------------------------------------------------------ lifecycle

    @PostMapping("/{cfrNo}/confirm-booking")
    @Operation(summary = "QUOTED -> BOOKING_CONFIRMED (requires >= 10% advance)")
    public ConsignmentDto.Response confirmBooking(@PathVariable String cfrNo) {
        return view(service.confirmBooking(cfrNo));
    }

    @PostMapping("/{cfrNo}/verify-documents")
    @Operation(summary = "BOOKING_CONFIRMED -> DOCUMENTS_VERIFIED (KYC, vehicle docs, Master Article List)")
    public ConsignmentDto.Response verifyDocuments(@PathVariable String cfrNo) {
        return view(service.verifyDocuments(cfrNo));
    }

    @PostMapping("/{cfrNo}/ready-for-loading")
    @Operation(summary = "DOCUMENTS_VERIFIED -> READY_FOR_LOADING (requires 100% payment)")
    public ConsignmentDto.Response ready(@PathVariable String cfrNo) {
        return view(service.markReadyForLoading(cfrNo));
    }

    @PostMapping("/{cfrNo}/dispatch")
    @Operation(summary = "READY_FOR_LOADING -> IN_TRANSIT")
    public ConsignmentDto.Response dispatch(@PathVariable String cfrNo) {
        return view(service.dispatch(cfrNo));
    }

    @PostMapping("/{cfrNo}/deliver")
    @Operation(summary = "IN_TRANSIT -> DELIVERED (stamps deliveredAt, starts claim window)")
    public ConsignmentDto.Response deliver(@PathVariable String cfrNo) {
        return view(service.deliver(cfrNo));
    }

    @PostMapping("/{cfrNo}/close")
    @Operation(summary = "DELIVERED -> CLOSED")
    public ConsignmentDto.Response close(@PathVariable String cfrNo) {
        return view(service.close(cfrNo));
    }

    @GetMapping("/{cfrNo}/cancellation-quote")
    @Operation(summary = "Preview cancellation deduction and refund")
    public ConsignmentDto.CancellationQuoteResponse quote(@PathVariable String cfrNo) {
        CancellationPolicy.Quote q = service.previewCancellation(cfrNo);
        return new ConsignmentDto.CancellationQuoteResponse(cfrNo, q.daysBeforePacking(), q.penaltyPercent(), q.deduction(), q.refund());
    }

    @PostMapping("/{cfrNo}/cancel")
    @Operation(summary = "Cancel: >=4 days before packing 0% deduction, 0-3 days 10% deduction")
    public ConsignmentDto.Response cancel(@PathVariable String cfrNo, @Valid @RequestBody(required = false) ConsignmentDto.CancelRequest r) {
        return view(service.cancel(cfrNo, r == null ? null : r.reason()));
    }

    private ConsignmentDto.Response view(Consignment c) {
        return ApmlMapper.toResponse(c, payments.totalPaid(c), payments.requiredAdvance(c));
    }
}
