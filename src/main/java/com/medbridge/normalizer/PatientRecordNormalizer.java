package com.medbridge.normalizer;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.Patient;
import org.springframework.stereotype.Component;

/**
 * Turns a validated raw record into a canonical Patient by running each field through
 * its normaliser. Only called AFTER validation passes, so the date is known to parse.
 */
@Component
public class PatientRecordNormalizer {

    private final DateNormalizer dateNormalizer;
    private final GenderNormalizer genderNormalizer;
    private final PhoneNormalizer phoneNormalizer;
    private final NameNormalizer nameNormalizer;

    public PatientRecordNormalizer(DateNormalizer dateNormalizer, GenderNormalizer genderNormalizer,
                                   PhoneNormalizer phoneNormalizer, NameNormalizer nameNormalizer) {
        this.dateNormalizer = dateNormalizer;
        this.genderNormalizer = genderNormalizer;
        this.phoneNormalizer = phoneNormalizer;
        this.nameNormalizer = nameNormalizer;
    }

    public Patient toPatient(PatientRecordDTO dto) {
        Patient patient = new Patient();
        patient.setMrn(trim(dto.getMrn()));
        patient.setFirstName(nameNormalizer.normalize(dto.getFirstName()));
        patient.setLastName(nameNormalizer.normalize(dto.getLastName()));
        patient.setDateOfBirth(dateNormalizer.normalize(dto.getDateOfBirth()));
        patient.setGender(genderNormalizer.normalize(dto.getGender()));
        patient.setPhone(phoneNormalizer.normalize(dto.getPhone()));
        patient.setCity(nameNormalizer.normalize(dto.getCity()));
        patient.setState(dto.getState() == null ? null : dto.getState().trim().toUpperCase());
        patient.setPostalCode(trim(dto.getPostalCode()));
        return patient;
    }

    private String trim(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
