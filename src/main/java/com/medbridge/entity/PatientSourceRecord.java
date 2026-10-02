package com.medbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Links a canonical Patient to one source system that described them, keeping that
 * source's own MRN and the raw text it sent.
 *
 * This is the audit trail: "show me the three original records that became this patient".
 *
 * The unique constraint enforces deduplication rule 1 at the database level:
 * within one source system, one MRN always means one person.
 */
@Entity
@Table(name = "patient_source_record", uniqueConstraints =
        @UniqueConstraint(name = "uk_source_record_source_mrn", columnNames = {"source_system_id", "source_mrn"}))
@Getter
@Setter
@NoArgsConstructor
public class PatientSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: the related row is only loaded if getPatient() is actually called.
    // The default for @ManyToOne is EAGER, which adds a query to every fetch.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_system_id", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "source_mrn", nullable = false, length = 50)
    private String sourceMrn;

    /** Exactly what the source sent, before any normalisation. Latest version wins on re-send. */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private LocalDateTime receivedAt;

    @UpdateTimestamp
    @Column(name = "last_received_at")
    private LocalDateTime lastReceivedAt;
}
