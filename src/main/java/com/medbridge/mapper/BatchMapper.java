package com.medbridge.mapper;

import com.medbridge.dto.BatchResponse;
import com.medbridge.dto.IngestionErrorResponse;
import com.medbridge.dto.IngestionResponse;
import com.medbridge.dto.SourceSystemResponse;
import com.medbridge.entity.IngestionBatch;
import com.medbridge.entity.IngestionError;
import com.medbridge.entity.SourceSystem;
import org.springframework.stereotype.Component;

@Component
public class BatchMapper {

    public IngestionResponse toIngestionResponse(IngestionBatch b) {
        return new IngestionResponse(b.getId(), b.getSourceSystem().getCode(), b.getFileName(), b.getStatus(),
                b.getTotalRecords(), b.getSuccessCount(), b.getFailureCount(), b.getNewPatientCount(),
                b.getMatchedPatientCount(), b.getConflictCount(), "/api/v1/batches/" + b.getId() + "/errors");
    }

    public BatchResponse toResponse(IngestionBatch b) {
        return new BatchResponse(b.getId(), b.getSourceSystem().getCode(), b.getFileName(), b.getStatus(),
                b.getStartedAt(), b.getCompletedAt(), b.getTotalRecords(), b.getSuccessCount(), b.getFailureCount(),
                b.getNewPatientCount(), b.getMatchedPatientCount(), b.getConflictCount(), b.getFailureReason());
    }

    public IngestionErrorResponse toErrorResponse(IngestionError e) {
        return new IngestionErrorResponse(e.getId(), e.getLineNumber(), e.getFieldName(), e.getErrorMessage(),
                e.getRawLine());
    }

    public SourceSystemResponse toSourceSystemResponse(SourceSystem s) {
        return new SourceSystemResponse(s.getId(), s.getCode(), s.getName(), s.getFormat(), s.isActive());
    }
}
