package com.medbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One file upload. Answers "what happened to the file the lab sent at 10:42?"
 * without anyone reading logs.
 */
@Entity
@Table(name = "ingestion_batch")
@Getter
@Setter
@NoArgsConstructor
public class IngestionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_system_id", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Records found in the file. Always successCount + failureCount once COMPLETED. */
    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    /** Successful records that created a new canonical patient. */
    @Column(name = "new_patient_count", nullable = false)
    private int newPatientCount;

    /** Successful records that matched an existing patient (deduplicated). */
    @Column(name = "matched_patient_count", nullable = false)
    private int matchedPatientCount;

    /** Field disagreements found while merging into existing patients (kept existing value, logged). */
    @Column(name = "conflict_count", nullable = false)
    private int conflictCount;

    /** Set only when status is FAILED: why the file could not be read. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
