package com.medbridge.parser;

import com.medbridge.dto.PatientRecordDTO;
import com.medbridge.entity.SourceFormat;
import com.medbridge.exception.ParseException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HOSPITAL feed: HL7 v2 ADT messages. Written by hand (no HAPI library) because only
 * two segments are needed and the mechanics are simple once the format is understood.
 *
 * Each line is a SEGMENT named by its first three characters:
 *   MSH  message header (who sent it, when, message type)  -> counted, otherwise ignored
 *   PID  patient identification                             -> one patient record
 *   anything else (PV1, OBX, vendor Z-segments...)          -> ignored
 *
 * Inside a segment, '|' separates FIELDS and '^' separates COMPONENTS.
 * Fields are positional, with no names. After splitting a PID segment on '|':
 *
 *   [3]  patient identifier   10001^^^HOSP^MR      -> component 0 is the MRN
 *   [5]  patient name         FATIMA^ZOYA^      -> family name FIRST, then given name
 *   [7]  date of birth        19980315             -> yyyyMMdd
 *   [8]  administrative sex   F
 *   [11] address              Hyderabad^TS^500001  -> city ^ state ^ postal code
 *   [13] phone                9876543210
 *
 * (Real HL7 addresses put street first; this feed sends city^state^zip.)
 */
@Component
public class Hl7RecordParser implements RecordParser {

    private static final int PID_IDENTIFIER = 3;
    private static final int PID_NAME = 5;
    private static final int PID_DOB = 7;
    private static final int PID_SEX = 8;
    private static final int PID_ADDRESS = 11;
    private static final int PID_PHONE = 13;

    @Override
    public List<PatientRecordDTO> parse(InputStream input) {
        List<PatientRecordDTO> records = new ArrayList<>();

        // readLine() splits on \n, \r\n and \r. Real HL7 separates segments with \r.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.startsWith("PID")) {
                    records.add(parsePid(line, lineNumber));
                }
            }
        } catch (IOException e) {
            throw new ParseException("Could not read HL7 file: " + e.getMessage(), e);
        }
        return records;
    }

    private PatientRecordDTO parsePid(String segment, int lineNumber) {
        // limit -1 keeps trailing empty fields. Without it, "a|b||".split("\\|") gives 2 values
        // instead of 4, and in a positional format every later field would shift.
        String[] fields = segment.split("\\|", -1);

        PatientRecordDTO dto = new PatientRecordDTO();
        dto.setMrn(component(field(fields, PID_IDENTIFIER), 0));
        dto.setLastName(component(field(fields, PID_NAME), 0));
        dto.setFirstName(component(field(fields, PID_NAME), 1));
        dto.setDateOfBirth(blankToNull(field(fields, PID_DOB)));
        dto.setGender(blankToNull(field(fields, PID_SEX)));
        dto.setCity(component(field(fields, PID_ADDRESS), 0));
        dto.setState(component(field(fields, PID_ADDRESS), 1));
        dto.setPostalCode(component(field(fields, PID_ADDRESS), 2));
        dto.setPhone(blankToNull(field(fields, PID_PHONE)));
        dto.setLineNumber(lineNumber);
        dto.setRawLine(segment);
        return dto;
    }

    /** A truncated segment returns "" for missing positions instead of ArrayIndexOutOfBoundsException. */
    private String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    private String component(String field, int index) {
        String[] parts = field.split("\\^", -1);
        return index < parts.length ? blankToNull(parts[index]) : null;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.HL7;
    }
}
