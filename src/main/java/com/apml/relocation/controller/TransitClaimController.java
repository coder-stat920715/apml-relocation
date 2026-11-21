package com.apml.relocation.controller;

import com.apml.relocation.dto.TransitDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.ClaimService;
import com.apml.relocation.service.TransitService;
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
@RequestMapping("/api/v1/consignments/{cfrNo}")
@RequiredArgsConstructor
@Tag(name = "Transit & Claims")
public class TransitClaimController {

    private final TransitService transit;
    private final ClaimService claims;

    @GetMapping("/transit-estimate")
    @Operation(summary = "Estimated transit days by consignment type (FTL 370 km/day, CUBES 250 km/day + 2, PART_LOAD 200 km/day + 3)")
    public TransitDto.EstimateResponse estimate(@PathVariable String cfrNo) {
        return transit.estimate(cfrNo);
    }

    @PostMapping("/transit-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public TransitDto.LogResponse addLog(@PathVariable String cfrNo, @Valid @RequestBody TransitDto.LogRequest r) {
        return ApmlMapper.toResponse(transit.addLog(cfrNo, r.location(), r.remarks()));
    }

    @GetMapping("/transit-logs")
    public List<TransitDto.LogResponse> logs(@PathVariable String cfrNo) {
        return transit.list(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lodge a damage claim (12h vehicles / 72h household goods after delivery; capped at declared cover)")
    public TransitDto.ClaimResponse lodge(@PathVariable String cfrNo, @Valid @RequestBody TransitDto.ClaimRequest r) {
        return ApmlMapper.toResponse(claims.lodge(cfrNo, r.surveyItemId(), r.damageType(), r.description(), r.claimedAmount()));
    }

    @GetMapping("/claims")
    public List<TransitDto.ClaimResponse> listClaims(@PathVariable String cfrNo) {
        return claims.list(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }
}
