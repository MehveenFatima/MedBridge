package com.medbridge.service;

import com.medbridge.dto.BatchResponse;
import com.medbridge.dto.IngestionErrorResponse;
import com.medbridge.dto.PageResponse;
import com.medbridge.exception.ResourceNotFoundException;
import com.medbridge.mapper.BatchMapper;
import com.medbridge.repository.IngestionBatchRepository;
import com.medbridge.repository.IngestionErrorRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ingestion history and error reports: "which records failed, on which line, and why". */
@Service
@Transactional(readOnly = true)
public class BatchService {

    private final IngestionBatchRepository batchRepository;
    private final IngestionErrorRepository errorRepository;
    private final BatchMapper mapper;

    public BatchService(IngestionBatchRepository batchRepository, IngestionErrorRepository errorRepository,
                        BatchMapper mapper) {
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        this.mapper = mapper;
    }

    public PageResponse<BatchResponse> findAll(Pageable pageable) {
        return PageResponse.from(batchRepository.findAll(pageable).map(mapper::toResponse));
    }

    public BatchResponse findById(Long id) {
        return batchRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
    }

    public PageResponse<IngestionErrorResponse> findErrors(Long batchId, Pageable pageable) {
        if (!batchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Batch", batchId);
        }
        return PageResponse.from(errorRepository.findByBatchId(batchId, pageable).map(mapper::toErrorResponse));
    }
}
