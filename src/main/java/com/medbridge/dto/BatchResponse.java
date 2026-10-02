package com.medbridge.dto;

import com.medbridge.entity.BatchStatus;

import java.time.LocalDateTime;

/** One upload in the ingestion history. */
public record BatchResponse(
        Long id,
        String sourceSystem,
        String fileName,
        BatchStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int totalRecords,
        int successCount,
        int failureCount,
        int newPatientCount,
        int matchedPatientCount,
        int conflictCount,
        String failureReason) {
}
