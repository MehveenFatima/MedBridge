package com.medbridge.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @Test
    void stripsPunctuationAndCountryCode() {
        assertEquals("9876543210", normalizer.normalize("+91-9876543210"));   // JSON feed
        assertEquals("9876543210", normalizer.normalize("9876543210"));       // CSV / HL7 feeds
        assertEquals("9876543210", normalizer.normalize("+91 98765 43210"));
        assertEquals("9876543210", normalizer.normalize("09876543210"));      // trunk zero
    }

    @Test
    void leavesWrongLengthsForTheValidatorToReject() {
        assertEquals("12345", normalizer.normalize("12345"));
    }

    @Test
    void blankBecomesNull() {
        assertNull(normalizer.normalize(null));
        assertNull(normalizer.normalize("   "));
        assertNull(normalizer.normalize("n/a"));
    }
}
