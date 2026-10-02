package com.medbridge.normalizer;

import com.medbridge.entity.Gender;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "F", "f", "Female", "FEMALE" -> Gender.FEMALE. HL7 uses "U" for unknown. */
@Component
public class GenderNormalizer {

    private static final Map<String, Gender> CODES = Map.of(
            "M", Gender.MALE, "MALE", Gender.MALE,
            "F", Gender.FEMALE, "FEMALE", Gender.FEMALE,
            "O", Gender.OTHER, "OTHER", Gender.OTHER,
            "U", Gender.UNKNOWN, "UNKNOWN", Gender.UNKNOWN);

    /** Missing or unrecognised values become UNKNOWN. The validator decides whether that is an error. */
    public Gender normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Gender.UNKNOWN;
        }
        return CODES.getOrDefault(raw.trim().toUpperCase(), Gender.UNKNOWN);
    }

    /** True for blank (not recorded) or a known code; false for something like "Z". */
    public boolean isRecognised(String raw) {
        return raw == null || raw.isBlank() || CODES.containsKey(raw.trim().toUpperCase());
    }
}
