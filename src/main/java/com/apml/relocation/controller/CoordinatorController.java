package com.apml.relocation.controller;

import com.apml.relocation.dto.CoordinatorDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.CoordinatorService;
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
@RequestMapping("/api/v1/consignments/{cfrNo}/coordinators")
@RequiredArgsConstructor
@Tag(name = "Coordinator Directory")
public class CoordinatorController {

    private final CoordinatorService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign or update a hub / field coordinator (phone numbers are returned masked)")
    public CoordinatorDto.Response assign(@PathVariable String cfrNo, @Valid @RequestBody CoordinatorDto.Request r) {
        return ApmlMapper.toResponse(service.assign(cfrNo, r.role(), r.name(), r.branchCode(), r.phoneNumber()));
    }

    @GetMapping
    public List<CoordinatorDto.Response> list(@PathVariable String cfrNo) {
        return service.list(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }
}
