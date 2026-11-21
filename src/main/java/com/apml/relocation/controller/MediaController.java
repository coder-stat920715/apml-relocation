package com.apml.relocation.controller;

import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.service.MediaService;
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
@RequestMapping("/api/v1/consignments/{cfrNo}/media")
@RequiredArgsConstructor
@Tag(name = "Inspection Media")
public class MediaController {

    private final MediaService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a PRE_PACKING / PACKED_AT_GODOWN / UNPACKED_AT_DELIVERY photo reference")
    public MediaDto.Response add(@PathVariable String cfrNo, @Valid @RequestBody MediaDto.Request r) {
        return ApmlMapper.toResponse(service.add(cfrNo, r));
    }

    @GetMapping
    public List<MediaDto.Response> list(@PathVariable String cfrNo) {
        return service.list(cfrNo).stream().map(ApmlMapper::toResponse).toList();
    }
}
