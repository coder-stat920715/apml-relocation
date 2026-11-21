package com.apml.relocation.controller;

import com.apml.relocation.dto.ComplianceDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consignments/{cfrNo}")
@RequiredArgsConstructor
@Tag(name = "Pre-loading Compliance")
public class ComplianceController {

    private final ComplianceService service;

    @PutMapping("/kyc")
    @Operation(summary = "Submit / replace a KYC document (numbers are masked in responses)")
    public ComplianceDto.KycResponse kyc(@PathVariable String cfrNo, @Valid @RequestBody ComplianceDto.KycRequest r) {
        return ApmlMapper.toResponse(service.submitKyc(cfrNo, r.type(), r.documentNumber(), r.photoCount()));
    }

    @GetMapping("/kyc")
    public List<ComplianceDto.KycResponse> listKyc(@PathVariable String cfrNo) {
        return service.listKyc(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }

    @GetMapping("/vehicle-checklists")
    @Operation(summary = "Vehicle checklists auto-created for two- and four-wheelers")
    public List<ComplianceDto.VehicleChecklistResponse> checklists(@PathVariable String cfrNo) {
        return service.listVehicleChecklists(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }

    @PutMapping("/vehicle-checklists/{surveyItemId}")
    @Operation(summary = "Update vehicle documents and fuel status (2W: empty tank, 4W: <= 24 L)")
    public ComplianceDto.VehicleChecklistResponse updateChecklist(@PathVariable String cfrNo, @PathVariable Long surveyItemId,
                                                                  @Valid @RequestBody ComplianceDto.VehicleChecklistRequest r) {
        return ApmlMapper.toResponse(service.updateVehicleChecklist(cfrNo, surveyItemId, r.rcBook(), r.validInsurance(),
                r.pucCertificate(), r.roadTaxReceipt(), r.fuelTankEmpty(), r.fuelLiters()));
    }

    @PostMapping("/master-article-list")
    @Operation(summary = "Submit the signed Master Article List (>= 24h before packing, no restricted items)")
    public ComplianceDto.MasterArticleResponse masterArticle(@PathVariable String cfrNo,
                                                             @Valid @RequestBody ComplianceDto.MasterArticleRequest r) {
        return ApmlMapper.toResponse(service.submitMasterArticle(cfrNo, r.signedBy(), r.restrictedItemsFound(),
                r.lines().stream().map(ApmlMapper::toDomain).toList()));
    }

    @GetMapping("/master-article-list")
    public ComplianceDto.MasterArticleResponse getMasterArticle(@PathVariable String cfrNo) {
        return ApmlMapper.toResponse(service.getMasterArticle(cfrNo));
    }

    @GetMapping("/compliance")
    @Operation(summary = "Evaluate all pre-loading compliance rules and list outstanding issues")
    public ComplianceDto.ComplianceStatusResponse compliance(@PathVariable String cfrNo) {
        List<String> violations = service.evaluate(cfrNo);
        return new ComplianceDto.ComplianceStatusResponse(cfrNo, violations.isEmpty(), violations);
    }
}
