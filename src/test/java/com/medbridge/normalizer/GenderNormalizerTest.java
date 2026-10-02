package com.medbridge.normalizer;

import com.medbridge.entity.Gender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenderNormalizerTest {

    private final GenderNormalizer normalizer = new GenderNormalizer();

    @ParameterizedTest
    @CsvSource({
            "F, FEMALE", "f, FEMALE", "Female, FEMALE", "FEMALE, FEMALE",
            "M, MALE", "Male, MALE",
            "O, OTHER", "U, UNKNOWN", "unknown, UNKNOWN"
    })
    void mapsEverySourceEncodingToOneCanonicalValue(String raw, Gender expected) {
        assertEquals(expected, normalizer.normalize(raw));
    }

    @Test
    void missingValueIsUnknownAndAcceptable() {
        assertEquals(Gender.UNKNOWN, normalizer.normalize(null));
        assertEquals(Gender.UNKNOWN, normalizer.normalize(" "));
        assertTrue(normalizer.isRecognised(null));
        assertTrue(normalizer.isRecognised(""));
    }

    @Test
    void unrecognisedCodeIsFlaggedForTheValidator() {
        assertEquals(Gender.UNKNOWN, normalizer.normalize("Z"));
        assertFalse(normalizer.isRecognised("Z"));
    }
}
