package com.medbridge.validator;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.normalizer.DateNormalizer;
import com.medbridge.normalizer.GenderNormalizer;
import com.medbridge.normalizer.PhoneNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks one raw record and returns EVERY problem in it, not just the first.
 *
 * Why collect instead of failing fast: a record with a blank MRN, an impossible date and
 * a short phone number should report three errors. Someone fixing a broken feed needs the
 * whole picture in one pass, not one error per re-upload.
 *
 * Rules:
 *   mrn           required, max 50 characters
 *   lastName      required, max 100 characters
 *   firstName     optional, max 100 characters
 *   dateOfBirth   required, must parse in a known format, cannot be in the future
 *   gender        optional, but if present must be a known code
 *   phone         optional, but if present must be 10 digits after normalisation
 */
@Component
public class PatientRecordValidator {

    static final int MAX_MRN = 50;
    static final int MAX_NAME = 100;

    private final DateNormalizer dateNormalizer;
    private final GenderNormalizer genderNormalizer;
    private final PhoneNormalizer phoneNormalizer;
    private final Clock clock;

    // Two constructors, so @Autowired tells Spring which one to use for the bean.
    @Autowired
    public PatientRecordValidator(DateNormalizer dateNormalizer, GenderNormalizer genderNormalizer,
                                  PhoneNormalizer phoneNormalizer) {
        this(dateNormalizer, genderNormalizer, phoneNormalizer, Clock.systemDefaultZone());
    }

    /** Clock is passed in so tests can pin "today". */
    PatientRecordValidator(DateNormalizer dateNormalizer, GenderNormalizer genderNormalizer,
                           PhoneNormalizer phoneNormalizer, Clock clock) {
        this.dateNormalizer = dateNormalizer;
        this.genderNormalizer = genderNormalizer;
        this.phoneNormalizer = phoneNormalizer;
        this.clock = clock;
    }

    public ValidationResult validate(PatientRecordDTO dto) {
        List<FieldError> errors = new ArrayList<>();

        if (isBlank(dto.getMrn())) {
            errors.add(new FieldError("mrn", "MRN is required"));
        } else if (dto.getMrn().trim().length() > MAX_MRN) {
            errors.add(new FieldError("mrn", "MRN is longer than " + MAX_MRN + " characters"));
        }

        if (isBlank(dto.getLastName())) {
            errors.add(new FieldError("lastName", "Last name is required"));
        } else if (dto.getLastName().trim().length() > MAX_NAME) {
            errors.add(new FieldError("lastName", "Last name is longer than " + MAX_NAME + " characters"));
        }

        if (!isBlank(dto.getFirstName()) && dto.getFirstName().trim().length() > MAX_NAME) {
            errors.add(new FieldError("firstName", "First name is longer than " + MAX_NAME + " characters"));
        }

        if (isBlank(dto.getDateOfBirth())) {
            errors.add(new FieldError("dateOfBirth", "Date of birth is required"));
        } else {
            LocalDate dob = dateNormalizer.normalize(dto.getDateOfBirth());
            if (dob == null) {
                errors.add(new FieldError("dateOfBirth", "Unrecognised or impossible date: '" + dto.getDateOfBirth()
                        + "' (expected yyyy-MM-dd, dd/MM/yyyy or yyyyMMdd)"));
            } else if (dob.isAfter(LocalDate.now(clock))) {
                errors.add(new FieldError("dateOfBirth", "Date of birth is in the future: " + dob));
            }
        }

        if (!genderNormalizer.isRecognised(dto.getGender())) {
            errors.add(new FieldError("gender", "Unrecognised gender code: '" + dto.getGender() + "'"));
        }

        String phone = phoneNormalizer.normalize(dto.getPhone());
        if (phone != null && phone.length() != 10) {
            errors.add(new FieldError("phone", "Phone must have 10 digits after normalisation, found "
                    + phone.length() + " ('" + dto.getPhone() + "')"));
        }

        return new ValidationResult(errors);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
