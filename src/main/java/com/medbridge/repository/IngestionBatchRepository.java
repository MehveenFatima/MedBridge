package com.medbridge.repository;

import com.medbridge.entity.IngestionBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, Long> {

    /** Batch history page. The entity graph loads each batch's source system in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "sourceSystem")
    Page<IngestionBatch> findAll(Pageable pageable);
}
