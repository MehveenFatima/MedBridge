package com.medbridge.repository;

import com.medbridge.entity.IngestionError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionErrorRepository extends JpaRepository<IngestionError, Long> {

    Page<IngestionError> findByBatchId(Long batchId, Pageable pageable);
}
