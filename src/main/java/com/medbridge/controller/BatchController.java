package com.medbridge.controller;

import com.medbridge.dto.BatchResponse;
import com.medbridge.dto.IngestionErrorResponse;
import com.medbridge.dto.PageResponse;
import com.medbridge.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batches", description = "Ingestion history and per-record error reports")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @Operation(summary = "Ingestion history (newest first)")
    @GetMapping
    public ResponseEntity<PageResponse<BatchResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(batchService.findAll(pageable));
    }

    @Operation(summary = "One batch with its counts")
    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(batchService.findById(id));
    }

    @Operation(summary = "What failed in a batch: line number, field, reason and the raw line")
    @GetMapping("/{id}/errors")
    public ResponseEntity<PageResponse<IngestionErrorResponse>> errors(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 50, sort = {"lineNumber", "id"}) Pageable pageable) {
        return ResponseEntity.ok(batchService.findErrors(id, pageable));
    }
}
