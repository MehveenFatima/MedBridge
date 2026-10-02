package com.medbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One problem with one record. A record with three bad fields produces three rows,
 * all sharing the same line number and raw line.
 */
@Entity
@Table(name = "ingestion_error", indexes = @Index(name = "idx_ingestion_error_batch", columnList = "batch_id"))
@Getter
@Setter
@NoArgsConstructor
public class IngestionError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private IngestionBatch batch;

    /** Line in the file for CSV and HL7; position in the array for JSON. */
    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** The original text of the rejected record, so support staff see what actually arrived. */
    @Column(name = "raw_line", columnDefinition = "TEXT")
    private String rawLine;
}
