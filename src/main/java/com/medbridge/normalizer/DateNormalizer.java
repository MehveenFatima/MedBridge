package com.medbridge.normalizer;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Three sources, three date conventions, one LocalDate:
 *   1998-03-15  (CSV,  ISO)
 *   15/03/1998  (JSON, day first)
 *   19980315    (HL7,  yyyyMMdd)
 *
 * STRICT resolution matters: with the default (SMART) style, "1998-02-30" is quietly
 * adjusted to 28 February instead of being rejected. 'uuuu' is required with STRICT.
 */
@Component
public class DateNormalizer {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));

    /**
     * @return the parsed date, or null if the value is missing or matches no known format.
     *         Returning null (not throwing) is deliberate: an unparseable date is a data
     *         problem to report with a line number, not a program crash.
     */
    public LocalDate normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // not this format; try the next one
            }
        }
        return null;
    }
}
