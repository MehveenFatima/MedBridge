package com.medbridge.controller;

import com.medbridge.dto.IngestionResponse;
import com.medbridge.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/** HTTP concerns only. All pipeline logic lives in IngestionService. */
@RestController
@RequestMapping("/api/v1/ingest")
@Tag(name = "Ingestion", description = "Upload a feed file from a source system")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 201 Created with a Location header pointing at the new batch.
     * The file is processed even if some records fail; failures are listed at errorsUrl.
     */
    @Operation(summary = "Ingest a CSV, JSON or HL7 file",
            description = "sourceSystem decides the parser: LAB -> CSV, CLINIC -> JSON, HOSPITAL -> HL7")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResponse> ingest(
            @Parameter(description = "The feed file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "LAB, CLINIC or HOSPITAL", example = "LAB")
            @RequestParam("sourceSystem") String sourceSystem) {

        IngestionResponse response = ingestionService.ingest(file, sourceSystem);
        return ResponseEntity.created(URI.create("/api/v1/batches/" + response.batchId())).body(response);
    }
}
