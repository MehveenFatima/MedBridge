package com.medbridge.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameNormalizerTest {

    private final NameNormalizer normalizer = new NameNormalizer();

    @Test
    void anyCasingBecomesTitlecase() {
        assertEquals("Fatima", normalizer.normalize("FATIMA"));    // HL7 sends uppercase
        assertEquals("Fatima", normalizer.normalize("fatima"));
        assertEquals("Fatima", normalizer.normalize("  Fatima "));
    }

    @Test
    void handlesMultiWordAndHyphenatedNames() {
        assertEquals("Mary Ann", normalizer.normalize("mary   ann"));
        assertEquals("Abdul-Rahman", normalizer.normalize("ABDUL-RAHMAN"));
    }

    @Test
    void blankBecomesNull() {
        assertNull(normalizer.normalize(null));
        assertNull(normalizer.normalize(" "));
    }
}
