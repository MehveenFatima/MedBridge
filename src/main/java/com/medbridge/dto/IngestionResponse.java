package com.medbridge.dto;

import com.medbridge.entity.BatchStatus;

/** Summary returned by POST /api/v1/ingest. */
public record IngestionResponse(
        Long batchId,
        String sourceSystem,
        String fileName,
        BatchStatus status,
        int totalRecords,
        int successCount,
        int failureCount,
        int newPatientCount,
        int matchedPatientCount,
        int conflictCount,
        String errorsUrl) {
}
