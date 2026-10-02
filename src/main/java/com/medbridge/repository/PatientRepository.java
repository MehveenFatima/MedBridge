package com.medbridge.repository;

import com.medbridge.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * No implementation class exists: Spring Data generates one at startup.
 * Derived queries are built from the method names, e.g.
 * findByMrn -> SELECT ... FROM patient WHERE mrn = ?
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findByMrn(String mrn);

    /**
     * Cross-source deduplication lookup (rule 2). Names are normalised to Titlecase before
     * this is called, so an exact match can use the (last_name, date_of_birth) index.
     * findFirst...OrderByIdAsc returns the oldest match if two people share both fields.
     */
    Optional<Patient> findFirstByLastNameAndDateOfBirthOrderByIdAsc(String lastName, LocalDate dateOfBirth);
}
