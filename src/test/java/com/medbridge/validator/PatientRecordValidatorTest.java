package com.medbridge.validator;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.normalizer.DateNormalizer;
import com.medbridge.normalizer.GenderNormalizer;
import com.medbridge.normalizer.PhoneNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientRecordValidatorTest {

    // "today" pinned to 1 October 2026 so the future-date rule is deterministic
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private final PatientRecordValidator validator =
            new PatientRecordValidator(new DateNormalizer(), new GenderNormalizer(), new PhoneNormalizer(), fixedClock);

    private PatientRecordDTO validRecord() {
        PatientRecordDTO dto = new PatientRecordDTO();
        dto.setMrn("10001");
        dto.setFirstName("Zoya");
        dto.setLastName("Fatima");
        dto.setDateOfBirth("1998-03-15");
        dto.setGender("F");
        dto.setPhone("+91-9876543210");
        return dto;
    }

    @Test
    void validRecordPasses() {
        assertTrue(validator.validate(validRecord()).isValid());
    }

    /** The design decision under test: report EVERY problem, not just the first. */
    @Test
    void collectsAllErrorsRatherThanStoppingAtTheFirst() {
        PatientRecordDTO dto = new PatientRecordDTO();
        dto.setMrn(" ");               // 1. blank MRN
        dto.setLastName(null);         // 2. missing last name
        dto.setDateOfBirth("BAD_DATE"); // 3. unparseable date
        dto.setGender("Z");            // 4. unknown gender code
        dto.setPhone("12345");         // 5. too short

        ValidationResult result = validator.validate(dto);

        assertFalse(result.isValid());
        List<String> fields = result.errors().stream().map(FieldError::field).toList();
        assertEquals(List.of("mrn", "lastName", "dateOfBirth", "gender", "phone"), fields);
    }

    @Test
    void rejectsFutureDateOfBirth() {
        PatientRecordDTO dto = validRecord();
        dto.setDateOfBirth("2030-05-12");

        ValidationResult result = validator.validate(dto);

        assertEquals(1, result.errors().size());
        assertEquals("dateOfBirth", result.errors().get(0).field());
        assertTrue(result.errors().get(0).message().contains("future"));
    }

    @Test
    void acceptsEveryFeedsDateFormat() {
        for (String dob : List.of("1998-03-15", "15/03/1998", "19980315")) {
            PatientRecordDTO dto = validRecord();
            dto.setDateOfBirth(dob);
            assertTrue(validator.validate(dto).isValid(), dob);
        }
    }

    @Test
    void optionalFieldsMayBeMissing() {
        PatientRecordDTO dto = validRecord();
        dto.setFirstName(null);
        dto.setGender(null);
        dto.setPhone(null);
        assertTrue(validator.validate(dto).isValid());
    }

    @Test
    void rejectsValuesLongerThanTheColumns() {
        PatientRecordDTO dto = validRecord();
        dto.setMrn("9".repeat(PatientRecordValidator.MAX_MRN + 1));

        ValidationResult result = validator.validate(dto);

        assertEquals("mrn", result.errors().get(0).field());
    }
}
