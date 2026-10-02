package com.medbridge.repository;

import com.medbridge.entity.PatientSourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientSourceRecordRepository extends JpaRepository<PatientSourceRecord, Long> {

    /** Deduplication rule 1: has this source already sent us this MRN? */
    Optional<PatientSourceRecord> findBySourceSystemIdAndSourceMrn(Long sourceSystemId, String sourceMrn);

    /**
     * All source records for one patient, with the source system loaded in the same query.
     * Without JOIN FETCH, reading getSourceSystem() on each row would fire one extra
     * query per row: the N+1 problem.
     */
    @Query("""
            SELECT r FROM PatientSourceRecord r
            JOIN FETCH r.sourceSystem
            WHERE r.patient.id = :patientId
            ORDER BY r.receivedAt ASC, r.id ASC
            """)
    List<PatientSourceRecord> findByPatientIdWithSourceSystem(@Param("patientId") Long patientId);
}
