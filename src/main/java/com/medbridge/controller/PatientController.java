package com.medbridge.controller;

import com.medbridge.dto.PageResponse;
import com.medbridge.dto.PatientResponse;
import com.medbridge.dto.SourceRecordResponse;
import com.medbridge.service.PatientService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Canonical patients after normalisation and deduplication")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @Operation(summary = "List patients (paged)", description = "e.g. ?page=0&size=20&sort=lastName,asc")
    @GetMapping
    public ResponseEntity<PageResponse<PatientResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = {"lastName", "firstName"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(patientService.findAll(pageable));
    }

    @Operation(summary = "Get one patient", description = "404 if the id does not exist")
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.findById(id));
    }

    @Operation(summary = "Original source records behind a patient",
            description = "Every raw CSV row, JSON object or HL7 PID segment that was reconciled into this patient")
    @GetMapping("/{id}/sources")
    public ResponseEntity<List<SourceRecordResponse>> sources(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.findSourceRecords(id));
    }

    @Operation(summary = "Search by MRN", description = "Returns a list: MRNs are not unique across sources")
    @GetMapping("/search")
    public ResponseEntity<List<PatientResponse>> searchByMrn(@RequestParam String mrn) {
        return ResponseEntity.ok(patientService.findByMrn(mrn));
    }
}
