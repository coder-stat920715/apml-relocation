package com.apml.relocation.controller;

import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.GcnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consignments/{cfrNo}/gcn")
@RequiredArgsConstructor
@Tag(name = "Goods Consignment Note (Lorry Receipt)")
public class GcnController {

    private final GcnService service;

    @PostMapping
    @Operation(summary = "Issue (201) or update (200) the GCN: Section 10/11 liability, delivery mode, packages, weights, materials")
    public ResponseEntity<GcnDto.Response> issue(@PathVariable String cfrNo, @Valid @RequestBody GcnDto.IssueRequest request) {
        boolean existed = service.exists(cfrNo);
        GcnDto.Response body = ApmlMapper.toResponse(service.issue(cfrNo, request));
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "Fetch the GCN, including returnable packing material due back on the day of unloading")
    public GcnDto.Response get(@PathVariable String cfrNo) {
        return ApmlMapper.toResponse(service.get(cfrNo));
    }
}
