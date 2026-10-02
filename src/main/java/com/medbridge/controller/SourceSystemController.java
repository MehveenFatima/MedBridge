package com.medbridge.controller;

import com.medbridge.dto.SourceSystemResponse;
import com.medbridge.service.SourceSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/source-systems")
@Tag(name = "Source systems", description = "The systems that send feeds, and their formats")
public class SourceSystemController {

    private final SourceSystemService sourceSystemService;

    public SourceSystemController(SourceSystemService sourceSystemService) {
        this.sourceSystemService = sourceSystemService;
    }

    @Operation(summary = "List configured source systems")
    @GetMapping
    public ResponseEntity<List<SourceSystemResponse>> list() {
        return ResponseEntity.ok(sourceSystemService.findAll());
    }
}
