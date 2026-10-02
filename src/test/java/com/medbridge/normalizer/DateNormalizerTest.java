package com.medbridge.normalizer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateNormalizerTest {

    private final DateNormalizer normalizer = new DateNormalizer();

    @Test
    void parsesAllThreeSourceFormatsToTheSameDate() {
        LocalDate expected = LocalDate.of(1998, 3, 15);
        assertEquals(expected, normalizer.normalize("1998-03-15"));   // CSV
        assertEquals(expected, normalizer.normalize("15/03/1998"));   // JSON
        assertEquals(expected, normalizer.normalize("19980315"));     // HL7
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(LocalDate.of(1998, 3, 15), normalizer.normalize("  1998-03-15 "));
    }

    @Test
    void rejectsImpossibleDatesInsteadOfAdjustingThem() {
        assertNull(normalizer.normalize("1998-13-45"));
        assertNull(normalizer.normalize("1998-02-30"));   // STRICT: not silently turned into 28 Feb
        assertNull(normalizer.normalize("45/13/1998"));
        assertNull(normalizer.normalize("19981345"));
    }

    @Test
    void returnsNullForMissingOrUnrecognisedValues() {
        assertNull(normalizer.normalize(null));
        assertNull(normalizer.normalize(""));
        assertNull(normalizer.normalize("BAD_DATE"));
        assertNull(normalizer.normalize("March 15, 1998"));
    }
}
