package com.apml.relocation.controller;

import com.apml.relocation.dto.PodDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.DeliveryPodService;
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
@RequestMapping("/api/v1/consignments/{cfrNo}/pod")
@RequiredArgsConstructor
@Tag(name = "Delivery POD")
public class DeliveryPodController {

    private final DeliveryPodService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record or replace the destination handover checklist (ID numbers are stored masked only)")
    public PodDto.Response record(@PathVariable String cfrNo, @Valid @RequestBody PodDto.Request request) {
        return ApmlMapper.toResponse(service.record(cfrNo, request));
    }

    @GetMapping
    public PodDto.Response get(@PathVariable String cfrNo) {
        return ApmlMapper.toResponse(service.get(cfrNo));
    }
}
