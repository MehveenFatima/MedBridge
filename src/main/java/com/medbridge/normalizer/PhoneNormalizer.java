package com.medbridge.normalizer;

import org.springframework.stereotype.Component;

/**
 * "+91-9876543210", "091 98765 43210", "9876543210" -> "9876543210".
 * Strips punctuation and the India country code / trunk prefix. Length is checked
 * by the validator, not here.
 */
@Component
public class PhoneNormalizer {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);            // +91 country code
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);            // leading trunk zero
        }
        return digits.isEmpty() ? null : digits;
    }
}
