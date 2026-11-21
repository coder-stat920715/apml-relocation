package com.apml.relocation.controller;

import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/enquiries")
@RequiredArgsConstructor
@Tag(name = "Enquiry & Survey")
public class EnquiryController {

    private final EnquiryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an enquiry with surveyed items; total CFT is auto-calculated")
    public EnquiryDto.Response create(@Valid @RequestBody EnquiryDto.CreateRequest request) {
        return ApmlMapper.toResponse(service.create(request));
    }

    @GetMapping("/{enquiryNo}")
    @Operation(summary = "Fetch an enquiry")
    public EnquiryDto.Response get(@PathVariable String enquiryNo) {
        return ApmlMapper.toResponse(service.get(enquiryNo));
    }

    @PostMapping("/{enquiryNo}/approve")
    @Operation(summary = "Approve an enquiry (must be within its 15-day validity)")
    public EnquiryDto.Response approve(@PathVariable String enquiryNo) {
        return ApmlMapper.toResponse(service.approve(enquiryNo));
    }
}
