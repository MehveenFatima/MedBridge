package com.medbridge.dto;

import com.medbridge.entity.SourceFormat;

import java.time.LocalDateTime;

/** One original record that contributed to a patient, exactly as its source sent it. */
public record SourceRecordResponse(
        Long id,
        String sourceSystem,
        SourceFormat format,
        String sourceMrn,
        String rawPayload,
        LocalDateTime receivedAt,
        LocalDateTime lastReceivedAt) {
}
