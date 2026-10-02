package com.medbridge.normalizer;

import org.springframework.stereotype.Component;

/**
 * "FATIMA", "fatima", "  Fatima " -> "Fatima". Also handles "mary ann" -> "Mary Ann"
 * and "abdul-rahman" -> "Abdul-Rahman".
 *
 * Known limitation: "McDonald" becomes "Mcdonald" and "O'BRIEN" becomes "O'brien".
 * A production system would keep the name exactly as given for display and use a
 * normalised copy only for matching.
 */
@Component
public class NameNormalizer {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.trim().replaceAll("\\s+", " ").toLowerCase();
        StringBuilder out = new StringBuilder(lower.length());
        boolean capitaliseNext = true;
        for (char c : lower.toCharArray()) {
            out.append(capitaliseNext ? Character.toUpperCase(c) : c);
            capitaliseNext = (c == ' ' || c == '-');
        }
        return out.toString();
    }
}
