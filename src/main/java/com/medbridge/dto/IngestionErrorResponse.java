package com.medbridge.dto;

/** One rejected field in one record: where it was, what was wrong, and what actually arrived. */
public record IngestionErrorResponse(
        Long id,
        Integer lineNumber,
        String fieldName,
        String errorMessage,
        String rawLine) {
}
