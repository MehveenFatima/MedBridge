package com.medbridge.dto;

import com.medbridge.entity.Gender;

import java.time.LocalDate;

/**
 * What the API returns for a patient. Deliberately separate from the Patient entity:
 *  1. the API only exposes what clients need (no internal timestamps),
 *  2. renaming a database column cannot silently break API clients,
 *  3. lazy JPA relationships never reach the JSON serializer.
 */
public record PatientResponse(
        Long id,
        String mrn,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String city,
        String state,
        String postalCode) {
}
