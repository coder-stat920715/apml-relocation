package com.apml.relocation.controller;

import com.apml.relocation.dto.TrackingDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.TrackingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
@Tag(name = "Tracking")
public class TrackingController {

    private final TrackingService service;

    @GetMapping("/lookup")
    @Operation(summary = "Resolve any of enquiryNo, cfrNo, gcnSerialNo or digitalGcNo to the full consignment picture")
    public TrackingDto.LookupResponse lookup(@RequestParam String reference) {
        return service.lookup(reference);
    }

    @GetMapping("/{digitalGcNo}")
    @Operation(summary = "APML-style milestone progress bar plus chronological hub event table")
    public TrackingDto.Timeline timeline(@PathVariable String digitalGcNo) {
        return service.timeline(digitalGcNo);
    }

    @PostMapping("/{digitalGcNo}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a hub scan; ARRIVED_DESTINATION_HUB starts the demurrage clock, "
            + "DELIVERED runs the payment + POD gate and opens the 12h/72h claim window")
    public TrackingDto.EventResponse record(@PathVariable String digitalGcNo, @Valid @RequestBody TrackingDto.EventRequest request) {
        return ApmlMapper.toResponse(service.record(digitalGcNo, request));
    }
}
