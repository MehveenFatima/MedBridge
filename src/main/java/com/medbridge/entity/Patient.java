package com.medbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The canonical patient: one row per real person, however many sources describe them.
 *
 * The (last_name, date_of_birth) index exists because that pair is the cross-source
 * deduplication lookup, run once for every incoming record.
 */
@Entity
@Table(name = "patient", indexes = {
        @Index(name = "idx_patient_mrn", columnList = "mrn"),
        @Index(name = "idx_patient_last_name_dob", columnList = "last_name, date_of_birth")
})
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MRN from the first source that registered this patient. Each source's own MRN lives on PatientSourceRecord. */
    @Column(name = "mrn", length = 50)
    private String mrn;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    // STRING, never ORDINAL: ORDINAL stores 0/1/2 and silently corrupts data if the enum is reordered
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10, nullable = false)
    private Gender gender = Gender.UNKNOWN;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
